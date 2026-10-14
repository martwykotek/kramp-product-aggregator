package com.kramp.aggregator.source.availability;

import java.time.LocalDate;

/** The availability service's wire shape; every field may be missing until checked. */
record AvailabilityResponse(
    Integer stockLevel, StockInfo.Warehouse warehouse, LocalDate expectedDelivery) {}
