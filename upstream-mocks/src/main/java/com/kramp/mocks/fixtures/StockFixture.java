package com.kramp.mocks.fixtures;

import java.util.Map;

/** Fictitious stock per warehouse code. */
public record StockFixture(String productId, Map<String, Integer> warehouses) {

  public int quantity(Warehouse warehouse) {
    return warehouses.getOrDefault(warehouse.code(), 0);
  }
}
