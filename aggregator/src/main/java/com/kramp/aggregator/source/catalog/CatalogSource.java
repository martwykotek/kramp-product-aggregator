package com.kramp.aggregator.source.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kramp.aggregator.aggregation.Criticality;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamNotFoundException;
import com.kramp.aggregator.source.UpstreamHttp;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Product content in the market's language; without it there is no product view. */
public final class CatalogSource implements ProductDataSource<CatalogProduct> {

  public static final String NAME = "catalog";
  public static final String NOT_IN_ASSORTMENT = "NOT_IN_ASSORTMENT";
  public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";

  private static final JsonMapper ERRORS = JsonMapper.builder().build();

  private final RestClient client;

  public CatalogSource(RestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Criticality criticality() {
    return Criticality.REQUIRED;
  }

  @Override
  public CatalogProduct fetch(ProductRequest request) {
    CatalogResponse body =
        UpstreamHttp.fetch(
            NAME,
            () ->
                client
                    .get()
                    .uri(
                        "/catalog/products/{id}?market={market}&lang={lang}",
                        request.productId(),
                        request.market().code(),
                        request.market().language())
                    .retrieve()
                    .onStatus(
                        status -> status.value() == 404,
                        (req, response) -> {
                          throw new UpstreamNotFoundException(NAME, notFoundReason(response));
                        })
                    .body(CatalogResponse.class));
    return toProduct(body);
  }

  /** The catalog says why in its own error shape; a 404 it cannot explain is still not found. */
  private static String notFoundReason(ClientHttpResponse response) throws IOException {
    try (InputStream in = response.getBody()) {
      ErrorBody error = ERRORS.readValue(in, ErrorBody.class);
      return error.error() != null && NOT_IN_ASSORTMENT.equals(error.error().code())
          ? NOT_IN_ASSORTMENT
          : PRODUCT_NOT_FOUND;
    } catch (JacksonException unreadable) {
      return PRODUCT_NOT_FOUND;
    }
  }

  private static CatalogProduct toProduct(CatalogResponse body) {
    if (body.id() == null
        || body.brand() == null
        || body.name() == null
        || body.language() == null) {
      throw UpstreamHttp.invalidBody(NAME);
    }
    List<CatalogProduct.Spec> specs = body.specs() == null ? List.of() : body.specs();
    List<String> images = body.images() == null ? List.of() : body.images();
    if (specs.stream()
            .anyMatch(spec -> spec == null || spec.label() == null || spec.value() == null)
        || images.stream().anyMatch(Objects::isNull)) {
      throw UpstreamHttp.invalidBody(NAME);
    }
    return new CatalogProduct(
        body.id(),
        body.brand(),
        body.ean(),
        body.salesUnit(),
        body.name(),
        body.description(),
        body.category(),
        List.copyOf(specs),
        List.copyOf(images),
        body.url(),
        body.language());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record ErrorBody(Detail error) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Detail(String code) {}
  }
}
