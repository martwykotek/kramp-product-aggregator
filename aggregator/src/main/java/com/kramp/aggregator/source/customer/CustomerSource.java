package com.kramp.aggregator.source.customer;

import com.kramp.aggregator.aggregation.Criticality;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamNotFoundException;
import com.kramp.aggregator.source.UpstreamHttp;
import java.util.List;
import java.util.Objects;
import org.springframework.web.client.RestClient;

/** The customer's segment and preferred brands; asked only when the request names a customer. */
public final class CustomerSource implements ProductDataSource<CustomerProfile> {

  public static final String NAME = "customer";
  public static final String UNKNOWN_CUSTOMER = "UNKNOWN_CUSTOMER";

  private final RestClient client;

  public CustomerSource(RestClient client) {
    this.client = client;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Criticality criticality() {
    return Criticality.OPTIONAL;
  }

  @Override
  public boolean isApplicable(ProductRequest request) {
    return request.customerId().isPresent();
  }

  @Override
  public CustomerProfile fetch(ProductRequest request) {
    CustomerResponse body =
        UpstreamHttp.fetch(
            NAME,
            () ->
                client
                    .get()
                    .uri("/customers/{id}", request.customerId().orElseThrow())
                    .retrieve()
                    .onStatus(
                        status -> status.value() == 404,
                        (req, response) -> {
                          throw new UpstreamNotFoundException(NAME, UNKNOWN_CUSTOMER);
                        })
                    .body(CustomerResponse.class));
    return toProfile(body);
  }

  private static CustomerProfile toProfile(CustomerResponse body) {
    List<String> brands = body.preferredBrands() == null ? List.of() : body.preferredBrands();
    if (body.segment() == null || brands.stream().anyMatch(Objects::isNull)) {
      throw UpstreamHttp.invalidBody(NAME);
    }
    return new CustomerProfile(body.segment(), List.copyOf(brands));
  }
}
