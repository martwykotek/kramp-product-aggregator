package com.kramp.mocks.pricing;

import java.math.BigDecimal;

public record PriceResponse(
    String productId,
    String currency,
    BigDecimal basePrice,
    int discountPercent,
    BigDecimal finalPrice,
    String priceType) {}
