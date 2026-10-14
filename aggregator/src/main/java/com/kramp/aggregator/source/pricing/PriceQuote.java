package com.kramp.aggregator.source.pricing;

import java.math.BigDecimal;
import java.util.Currency;

/** A price in the market's currency, with the customer's discount already applied. */
public record PriceQuote(
    Currency currency,
    BigDecimal basePrice,
    int discountPercent,
    BigDecimal finalPrice,
    String priceType) {}
