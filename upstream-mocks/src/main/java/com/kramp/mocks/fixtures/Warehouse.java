package com.kramp.mocks.fixtures;

/** Real Kramp distribution centres (stock levels in the fixtures are fictitious). */
public enum Warehouse {
  NL_VAR("NL-VAR", "Varsseveld", "NL"),
  DE_STR("DE-STR", "Strullendorf", "DE"),
  FR_POI("FR-POI", "Poitiers", "FR"),
  GB_BIG("GB-BIG", "Biggleswade", "GB"),
  PL_MOD("PL-MOD", "Modła Królewska", "PL");

  /** Varsseveld is Kramp's main distribution centre. */
  public static final Warehouse CENTRAL = NL_VAR;

  private final String code;
  private final String city;
  private final String country;

  Warehouse(String code, String city, String country) {
    this.code = code;
    this.city = city;
    this.country = country;
  }

  public String code() {
    return code;
  }

  public String city() {
    return city;
  }

  public String country() {
    return country;
  }
}
