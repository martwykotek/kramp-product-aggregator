package com.kramp.aggregator.source.pricing;

import com.kramp.aggregator.aggregation.Criticality;
import com.kramp.aggregator.aggregation.ProductDataSource;
import com.kramp.aggregator.aggregation.ProductRequest;
import com.kramp.aggregator.aggregation.UpstreamNotFoundException;
import com.kramp.aggregator.source.UpstreamHttp;
import java.util.Currency;
import org.springframework.web.client.RestClient;

/** The market price, discounted for the customer when one is given. */
public final class PricingSource implements ProductDataSource<PriceQuote> {

  public static final String NAME = "pricing";
  public static final String NO_PRICE = "NO_PRICE";

  private final RestClient client;

  public PricingSource(RestClient client) {
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
  public PriceQuote fetch(ProductRequest request) {
    PricingResponse body =
        UpstreamHttp.fetch(
            NAME,
            () ->
                client
                    .get()
                    .uri(
                        uri ->
                            uri.path("/pricing/prices/{id}")
                                .queryParam("market", request.market().code())
                                .queryParamIfPresent("customerId", request.customerId())
                                .build(request.productId()))
                    .retrieve()
                    .onStatus(
                        status -> status.value() == 404,
                        (req, response) -> {
                          throw new UpstreamNotFoundException(NAME, NO_PRICE);
                        })
                    .body(PricingResponse.class));
    return toQuote(body, request.market().currency());
  }

  /**
   * A price we cannot trust is never shown: wrong currency or amounts that contradict each other.
   */
  private static PriceQuote toQuote(PricingResponse body, Currency marketCurrency) {
    if (body.currency() == null
        || body.basePrice() == null
        || body.discountPercent() == null
        || body.finalPrice() == null
        || !marketCurrency.getCurrencyCode().equals(body.currency())) {
      throw UpstreamHttp.invalidBody(NAME);
    }
    int discount = body.discountPercent();
    if (body.finalPrice().signum() < 0
        || body.finalPrice().compareTo(body.basePrice()) > 0
        || discount < 0
        || discount > 100) {
      throw UpstreamHttp.invalidBody(NAME);
    }
    return new PriceQuote(
        marketCurrency, body.basePrice(), discount, body.finalPrice(), body.priceType());
  }
}
