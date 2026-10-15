package com.kramp.aggregator.api.grpc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.kramp.aggregator.testing.SharedWireMock.UPSTREAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.kramp.aggregator.api.grpc.v1.GetProductRequest;
import com.kramp.aggregator.api.grpc.v1.ProductAggregatorGrpc;
import com.kramp.aggregator.api.grpc.v1.ProductView;
import com.kramp.aggregator.presentation.ProductViewService;
import com.kramp.aggregator.testing.SharedWireMock;
import com.kramp.aggregator.testing.UpstreamStubs;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.stub.MetadataUtils;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {"spring.grpc.server.inprocess.name=product-grpc-it", "spring.grpc.server.port=0"})
@AutoConfigureMockMvc
class ProductGrpcIT {

  static final String UPSTREAM_TEXT = "UPSTREAM-SECRET-42 failed at db-7";

  @Autowired MockMvc mvc;
  @Autowired CircuitBreakerRegistry breakers;
  @MockitoSpyBean ProductViewService views;

  ManagedChannel channel;
  ProductAggregatorGrpc.ProductAggregatorBlockingStub grpc;

  @DynamicPropertySource
  static void upstreams(DynamicPropertyRegistry registry) {
    SharedWireMock.register(registry);
  }

  @BeforeEach
  void resetUpstreamsAndBreakersAndConnect() {
    UPSTREAMS.resetAll();
    breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    channel = InProcessChannelBuilder.forName("product-grpc-it").directExecutor().build();
    grpc = ProductAggregatorGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void disconnect() {
    channel.shutdownNow();
  }

  static GetProductRequest request(String productId, String market) {
    return GetProductRequest.newBuilder().setProductId(productId).setMarket(market).build();
  }

  /** The REST answer to W6103 in pl-PL for customer C-1002. */
  DocumentContext restView() throws Exception {
    String json =
        mvc.perform(
                get("/api/v1/products/{productId}", "W6103")
                    .param("market", "pl-PL")
                    .header("X-Customer-Id", "C-1002"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.parse(json);
  }

  static BigDecimal decimal(Object restNumber) {
    return restNumber == null ? null : new BigDecimal(restNumber.toString());
  }

  static MappingBuilder catalogAnswer(int status, String body) {
    return WireMock.get(urlPathMatching(UpstreamStubs.CATALOG))
        .willReturn(
            aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body));
  }

  @Nested
  class GetProduct {

    static Stream<Arguments> upstreamStates() {
      return Stream.of(
          arguments("all upstreams answer", null, "AVAILABLE", false),
          arguments(
              "pricing answers 500",
              UpstreamStubs.serverError(UpstreamStubs.PRICING),
              "UNAVAILABLE",
              true));
    }

    // proves: REQ-API-GRPC-001
    @ParameterizedTest(name = "{0}")
    @MethodSource("upstreamStates")
    void shouldReturnSameViewAsRestWhenAllUpstreamsAnswer(
        String name, MappingBuilder failure, String pricingStatus, boolean degraded)
        throws Exception {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      if (failure != null) {
        UPSTREAMS.stubFor(failure);
      }

      // When
      ProductView view =
          grpc.getProduct(request("W6103", "pl-PL").toBuilder().setCustomerId("C-1002").build());
      DocumentContext rest = restView();

      // Then
      assertThat(view.getPricing().getStatus()).isEqualTo(pricingStatus);
      assertThat(view.getMeta().getDegraded()).isEqualTo(degraded);
      if (failure == null) {
        assertThat(view.getPricing().getFinalPrice()).isEqualTo("57.11");
      }
      assertThat(view.getProductId()).isEqualTo(rest.read("$.productId"));
      assertThat(view.getMarket()).isEqualTo(rest.read("$.market"));
      assertThat(view.getLanguage()).isEqualTo(rest.read("$.language"));
      assertThat(view.getProduct().getName()).isEqualTo(rest.read("$.product.name"));
      assertThat(view.getProduct().getBrand()).isEqualTo(rest.read("$.product.brand"));
      assertThat(view.getPricing().getStatus()).isEqualTo(rest.read("$.pricing.status"));
      assertThat(view.getPricing().hasCurrency() ? view.getPricing().getCurrency() : null)
          .isEqualTo(rest.read("$.pricing.currency"));
      assertThat(
              view.getPricing().hasFinalPrice() ? decimal(view.getPricing().getFinalPrice()) : null)
          .isEqualTo(decimal(rest.read("$.pricing.finalPrice")));
      assertThat(view.getPricing().hasLabel() ? view.getPricing().getLabel() : null)
          .isEqualTo(rest.read("$.pricing.label"));
      assertThat(view.getAvailability().getStatus()).isEqualTo(rest.read("$.availability.status"));
      assertThat(view.getAvailability().getWarehouse().getCode())
          .isEqualTo(rest.read("$.availability.warehouse.code"));
      assertThat(view.getAvailability().getWarehouse().getCity())
          .isEqualTo(rest.read("$.availability.warehouse.city"));
      assertThat(view.getPersonalization().getStatus())
          .isEqualTo(rest.read("$.personalization.status"));
      assertThat(view.getPersonalization().getSegment())
          .isEqualTo(rest.read("$.personalization.segment"));
      assertThat(view.getPersonalization().getPreferredBrand())
          .isEqualTo(rest.read("$.personalization.preferredBrand"));
      assertThat(view.getMeta().getDegraded()).isEqualTo(rest.read("$.meta.degraded"));
      Map<String, Map<String, Object>> restSources = rest.read("$.meta.sources");
      assertThat(
              view.getMeta().getSourcesList().stream()
                  .map(source -> source.getName() + "=" + source.getStatus())
                  .toList())
          .isEqualTo(
              restSources.entrySet().stream()
                  .map(source -> source.getKey() + "=" + source.getValue().get("status"))
                  .toList())
          .hasSize(4);
    }
  }

  @Nested
  class Failures {

    static Stream<Arguments> failingRequests() {
      return Stream.of(
          arguments(
              "unsupported market",
              request("W6103", "xx-XX"),
              null,
              Status.Code.INVALID_ARGUMENT,
              List.of("pl-PL", "en-GB")),
          arguments(
              "missing market",
              GetProductRequest.newBuilder().setProductId("W6103").build(),
              null,
              Status.Code.INVALID_ARGUMENT,
              List.of("market is required")),
          arguments(
              "malformed id",
              request("W6103$", "pl-PL"),
              null,
              Status.Code.INVALID_ARGUMENT,
              List.of()),
          arguments(
              "unknown product",
              request("W6103", "pl-PL"),
              catalogAnswer(
                  404,
                  "{\"error\":{\"code\":\"PRODUCT_NOT_FOUND\",\"message\":\"%s\"}}"
                      .formatted(UPSTREAM_TEXT)),
              Status.Code.NOT_FOUND,
              List.of("W6103")),
          arguments(
              "not in market",
              request("10112PB", "pl-PL"),
              catalogAnswer(
                  404,
                  "{\"error\":{\"code\":\"NOT_IN_ASSORTMENT\",\"message\":\"%s\"}}"
                      .formatted(UPSTREAM_TEXT)),
              Status.Code.NOT_FOUND,
              List.of("10112PB", "pl-PL")),
          arguments(
              "catalog 500",
              request("W6103", "pl-PL"),
              catalogAnswer(500, UPSTREAM_TEXT),
              Status.Code.UNAVAILABLE,
              List.of()),
          arguments(
              "catalog slow",
              request("W6103", "pl-PL"),
              UpstreamStubs.slow(UpstreamStubs.CATALOG, 1000),
              Status.Code.DEADLINE_EXCEEDED,
              List.of()));
    }

    // proves: REQ-API-GRPC-002
    @ParameterizedTest(name = "{0}")
    @MethodSource("failingRequests")
    void shouldMapFailureToGrpcStatusWhenRequestFails(
        String name,
        GetProductRequest request,
        MappingBuilder catalog,
        Status.Code expected,
        List<String> mentioned) {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      if (catalog != null) {
        UPSTREAMS.stubFor(catalog);
      }

      // When
      StatusRuntimeException failure =
          catchThrowableOfType(StatusRuntimeException.class, () -> grpc.getProduct(request));

      // Then
      String description = failure.getStatus().getDescription();
      assertThat(failure.getStatus().getCode()).isEqualTo(expected);
      assertThat(description)
          .isNotBlank()
          .doesNotContain("UPSTREAM-SECRET-42", "Internal Server Error");
      assertThat(mentioned).allSatisfy(text -> assertThat(description).contains(text));
    }
  }

  @Nested
  @ExtendWith(OutputCaptureExtension.class)
  class UnexpectedFailure {

    static final String BUG_TEXT = "BUG-SECRET-7 mapper field was null";

    // proves: REQ-API-GRPC-002
    @Test
    void shouldAnswerInternalAndLogOnceWhenViewServiceFailsUnexpectedly(CapturedOutput output) {
      // Given
      doThrow(new IllegalStateException(BUG_TEXT)).when(views).view(any(), any(), any());
      Metadata metadata = new Metadata();
      metadata.put(Correlation.HEADER, "grpc-corr-bug");

      // When
      StatusRuntimeException failure =
          catchThrowableOfType(
              StatusRuntimeException.class,
              () ->
                  grpc.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
                      .getProduct(
                          request("W6103", "pl-PL").toBuilder().setCustomerId("C-1002").build()));

      // Then
      assertThat(failure.getStatus().getCode()).isEqualTo(Status.Code.INTERNAL);
      assertThat(failure.getStatus().getDescription())
          .isEqualTo("The request could not be processed")
          .doesNotContain("BUG-SECRET-7");
      assertThat(
              output.getAll().lines().filter(line -> line.contains("request_failed_unexpectedly")))
          .singleElement()
          .asString()
          .contains("[grpc-corr-bug]");
      assertThat(output.getAll()).doesNotContain("C-1002");
    }
  }

  @Nested
  class Threading {

    @Test
    void shouldServeGetProductOnVirtualThreadWhenClientCalls() {
      // Given
      UpstreamStubs.standard(UPSTREAMS);
      AtomicReference<Thread> handler = new AtomicReference<>();
      doAnswer(
              call -> {
                handler.set(Thread.currentThread());
                return call.callRealMethod();
              })
          .when(views)
          .view(any(), any(), any());

      // When
      grpc.getProduct(request("W6103", "pl-PL"));

      // Then
      assertThat(handler.get().isVirtual()).as("GetProduct served on %s", handler.get()).isTrue();
    }
  }

  @Nested
  class Correlation {

    static final Metadata.Key<String> HEADER =
        Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    ProductView getProductWith(String correlationId) {
      Metadata metadata = new Metadata();
      if (correlationId != null) {
        metadata.put(HEADER, correlationId);
      }
      return grpc.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata))
          .getProduct(request("W6103", "pl-PL"));
    }

    @Test
    void shouldUseClientCorrelationIdWhenMetadataCarriesOne() {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When
      ProductView view = getProductWith("grpc-corr-1");

      // Then
      assertThat(view.getMeta().getCorrelationId()).isEqualTo("grpc-corr-1");
      UPSTREAMS.verify(
          getRequestedFor(urlPathMatching(UpstreamStubs.CATALOG))
              .withHeader("X-Correlation-Id", equalTo("grpc-corr-1")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = "has space")
    void shouldGenerateCorrelationIdWhenMetadataMissingOrInvalid(String header) {
      // Given
      UpstreamStubs.standard(UPSTREAMS);

      // When
      ProductView view = getProductWith(header);

      // Then
      String generated = view.getMeta().getCorrelationId();
      assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
    }
  }
}
