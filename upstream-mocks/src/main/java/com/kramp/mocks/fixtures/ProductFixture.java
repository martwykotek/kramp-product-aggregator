package com.kramp.mocks.fixtures;

import java.util.List;
import java.util.Map;

public record ProductFixture(
    String id,
    String brand,
    String ean,
    String salesUnit,
    List<String> images,
    List<String> notSoldIn,
    List<SpecFixture> specs,
    Map<String, LocalizedContent> content) {}
