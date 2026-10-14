package com.kramp.mocks.fixtures;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;

/** Markets as the upstreams see them: time zone, serving warehouse and price-list currency. */
public enum MockMarket {
  NL_NL("nl-NL", "Europe/Amsterdam", Warehouse.NL_VAR, "EUR"),
  NL_BE("nl-BE", "Europe/Brussels", Warehouse.NL_VAR, "EUR"),
  FR_BE("fr-BE", "Europe/Brussels", Warehouse.NL_VAR, "EUR"),
  FR_FR("fr-FR", "Europe/Paris", Warehouse.FR_POI, "EUR"),
  DE_DE("de-DE", "Europe/Berlin", Warehouse.DE_STR, "EUR"),
  DE_AT("de-AT", "Europe/Vienna", Warehouse.DE_STR, "EUR"),
  PL_PL("pl-PL", "Europe/Warsaw", Warehouse.PL_MOD, "PLN"),
  EN_GB("en-GB", "Europe/London", Warehouse.GB_BIG, "GBP");

  private final String code;
  private final ZoneId zone;
  private final Warehouse localWarehouse;
  private final String currency;

  MockMarket(String code, String zone, Warehouse localWarehouse, String currency) {
    this.code = code;
    this.zone = ZoneId.of(zone);
    this.localWarehouse = localWarehouse;
    this.currency = currency;
  }

  public String code() {
    return code;
  }

  public ZoneId zone() {
    return zone;
  }

  public Warehouse localWarehouse() {
    return localWarehouse;
  }

  public String currency() {
    return currency;
  }

  public static Optional<MockMarket> fromCode(String code) {
    return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst();
  }
}
