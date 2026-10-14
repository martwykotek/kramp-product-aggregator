package com.kramp.aggregator.source.catalog;

import java.util.List;

/** The catalog's wire shape; every field may be missing until {@link CatalogSource} checks it. */
record CatalogResponse(
    String id,
    String brand,
    String ean,
    String salesUnit,
    String name,
    String description,
    String category,
    List<CatalogProduct.Spec> specs,
    List<String> images,
    String url,
    String language) {}
