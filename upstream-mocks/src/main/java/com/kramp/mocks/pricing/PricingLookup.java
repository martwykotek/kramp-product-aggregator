package com.kramp.mocks.pricing;

import com.kramp.mocks.fixtures.Fixtures;
import com.kramp.mocks.fixtures.MockMarket;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Market price list in the market currency, minus the customer's agreed discount. */
@Component
public class PricingLookup {

  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

  private final Fixtures fixtures;

  public PricingLookup(Fixtures fixtures) {
    this.fixtures = fixtures;
  }

  public Optional<PriceResponse> quote(String productId, MockMarket market, String customerId) {
    return fixtures
        .product(productId)
        .filter(product -> !product.notSoldIn().contains(market.code()))
        .flatMap(product -> fixtures.price(productId, market.currency()))
        .map(
            base -> {
              int discount = customerId == null ? 0 : fixtures.discount(customerId);
              BigDecimal finalPrice =
                  base.multiply(BigDecimal.valueOf(100L - discount))
                      .divide(HUNDRED, 2, RoundingMode.HALF_UP);
              return new PriceResponse(
                  productId, market.currency(), base, discount, finalPrice, "NET");
            });
  }
}
