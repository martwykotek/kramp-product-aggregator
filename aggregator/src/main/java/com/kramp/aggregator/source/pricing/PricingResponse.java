package com.kramp.aggregator.source.pricing;

import java.math.BigDecimal;

/** The pricing service's wire shape; every field may be missing until checked. */
record PricingResponse(
    String currency,
    BigDecimal basePrice,
    Integer discountPercent,
    BigDecimal finalPrice,
    String priceType) {}
