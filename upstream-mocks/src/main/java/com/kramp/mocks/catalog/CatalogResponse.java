package com.kramp.mocks.catalog;

import java.util.List;

public record CatalogResponse(
    String id,
    String brand,
    String ean,
    String salesUnit,
    String name,
    String description,
    String category,
    List<Spec> specs,
    List<String> images,
    String url,
    String language) {

  public record Spec(String label, String value) {}
}
