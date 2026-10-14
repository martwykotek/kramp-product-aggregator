package com.kramp.mocks.availability;

import com.kramp.mocks.fixtures.Fixtures;
import com.kramp.mocks.fixtures.MockMarket;
import com.kramp.mocks.fixtures.StockFixture;
import com.kramp.mocks.fixtures.Warehouse;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Local warehouse first, then the central warehouse (one day longer), else a restock date. */
@Component
public class AvailabilityLookup {

  static final int CENTRAL_EXTRA_DAYS = 1;
  static final int RESTOCK_WORKING_DAYS = 10;

  private final Fixtures fixtures;
  private final DeliveryDateCalculator delivery;

  public AvailabilityLookup(Fixtures fixtures, DeliveryDateCalculator delivery) {
    this.fixtures = fixtures;
    this.delivery = delivery;
  }

  public Optional<AvailabilityResponse> stock(String productId, MockMarket market) {
    return fixtures.stock(productId).map(stock -> answer(productId, stock, market));
  }

  private AvailabilityResponse answer(String productId, StockFixture stock, MockMarket market) {
    Warehouse local = market.localWarehouse();
    int localQuantity = stock.quantity(local);
    if (localQuantity > 0) {
      return response(productId, localQuantity, local, delivery.expectedDelivery(market.zone(), 0));
    }
    int centralQuantity = stock.quantity(Warehouse.CENTRAL);
    if (local != Warehouse.CENTRAL && centralQuantity > 0) {
      return response(
          productId,
          centralQuantity,
          Warehouse.CENTRAL,
          delivery.expectedDelivery(market.zone(), CENTRAL_EXTRA_DAYS));
    }
    return response(
        productId, 0, local, delivery.expectedDelivery(market.zone(), RESTOCK_WORKING_DAYS));
  }

  private static AvailabilityResponse response(
      String productId, int quantity, Warehouse warehouse, LocalDate expected) {
    return new AvailabilityResponse(
        productId,
        quantity,
        new AvailabilityResponse.WarehouseView(
            warehouse.code(), warehouse.city(), warehouse.country()),
        expected);
  }
}
