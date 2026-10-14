package com.kramp.aggregator.source.catalog;

import java.util.List;

/** Product content in the language the catalog served; `ean` and the texts may be absent. */
public record CatalogProduct(
    String id,
    String brand,
    String ean,
    String salesUnit,
    String name,
    String description,
    String category,
    List<Spec> specs,
    List<String> images,
    String shopUrl,
    String language) {

  public record Spec(String label, String value) {}
}
