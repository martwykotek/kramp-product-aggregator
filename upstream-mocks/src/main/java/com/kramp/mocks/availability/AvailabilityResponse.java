package com.kramp.mocks.availability;

import java.time.LocalDate;

public record AvailabilityResponse(
    String productId, int stockLevel, WarehouseView warehouse, LocalDate expectedDelivery) {

  public record WarehouseView(String code, String city, String country) {}
}
