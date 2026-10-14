package com.kramp.aggregator.source.availability;

import java.time.LocalDate;

/** Stock in the warehouse that serves the market, and when an order would arrive. */
public record StockInfo(int stockLevel, Warehouse warehouse, LocalDate expectedDelivery) {

  public record Warehouse(String code, String city, String country) {}
}
