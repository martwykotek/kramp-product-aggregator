package com.kramp.aggregator.presentation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The aggregated product as clients see it; serialised as is for REST (field order is the JSON
 * order) and mapped field by field for gRPC.
 */
public record ProductView(
    String productId,
    String market,
    String language,
    ProductSection product,
    PricingSection pricing,
    AvailabilitySection availability,
    PersonalizationSection personalization,
    Meta meta) {

  public record ProductSection(
      String name,
      String brand,
      String description,
      String category,
      String ean,
      String salesUnit,
      List<Spec> specs,
      List<String> images,
      String shopUrl) {}

  public record Spec(String label, String value) {}

  /** Price fields are null unless the status is AVAILABLE; the label says why not. */
  public record PricingSection(
      String status,
      String currency,
      String priceType,
      BigDecimal basePrice,
      Integer discountPercent,
      BigDecimal finalPrice,
      Formatted formatted,
      String label) {}

  public record Formatted(String basePrice, String finalPrice) {}

  public record AvailabilitySection(
      String status,
      Integer stockLevel,
      Warehouse warehouse,
      LocalDate expectedDelivery,
      String label) {}

  public record Warehouse(String code, String city, String country) {}

  public record PersonalizationSection(String status, String segment, Boolean preferredBrand) {}

  public record Meta(boolean degraded, String correlationId, Map<String, SourceReport> sources) {}

  public record SourceReport(String status, long durationMs) {}
}
