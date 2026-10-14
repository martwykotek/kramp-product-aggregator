package com.kramp.mocks.behavior;

import java.util.Arrays;
import java.util.Optional;

/** The four simulated upstream services and the URL prefix each one serves. */
public enum Upstream {
  CATALOG("catalog", "/catalog/"),
  PRICING("pricing", "/pricing/"),
  AVAILABILITY("availability", "/availability/"),
  CUSTOMER("customer", "/customers/");

  private final String id;
  private final String pathPrefix;

  Upstream(String id, String pathPrefix) {
    this.id = id;
    this.pathPrefix = pathPrefix;
  }

  public String id() {
    return id;
  }

  public static Optional<Upstream> fromId(String id) {
    return Arrays.stream(values()).filter(u -> u.id.equals(id)).findFirst();
  }

  public static Optional<Upstream> fromPath(String path) {
    return Arrays.stream(values()).filter(u -> path.startsWith(u.pathPrefix)).findFirst();
  }
}
