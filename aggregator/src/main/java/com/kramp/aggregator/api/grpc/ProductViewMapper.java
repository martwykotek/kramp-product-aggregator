package com.kramp.aggregator.api.grpc;

import com.kramp.aggregator.api.grpc.v1.Availability;
import com.kramp.aggregator.api.grpc.v1.Formatted;
import com.kramp.aggregator.api.grpc.v1.Meta;
import com.kramp.aggregator.api.grpc.v1.Personalization;
import com.kramp.aggregator.api.grpc.v1.Pricing;
import com.kramp.aggregator.api.grpc.v1.Product;
import com.kramp.aggregator.api.grpc.v1.ProductView;
import com.kramp.aggregator.api.grpc.v1.SourceReport;
import com.kramp.aggregator.api.grpc.v1.Spec;
import com.kramp.aggregator.api.grpc.v1.Warehouse;
import com.kramp.aggregator.presentation.ProductView.AvailabilitySection;
import com.kramp.aggregator.presentation.ProductView.PersonalizationSection;
import com.kramp.aggregator.presentation.ProductView.PricingSection;
import com.kramp.aggregator.presentation.ProductView.ProductSection;
import java.math.BigDecimal;
import java.util.function.Consumer;

/**
 * Mirrors the REST view field by field: the same status strings, money as decimal strings, sources
 * in `meta.sources` order. A field that is null in JSON is left unset.
 */
final class ProductViewMapper {

  private ProductViewMapper() {}

  static ProductView toProto(com.kramp.aggregator.presentation.ProductView view) {
    return ProductView.newBuilder()
        .setProductId(view.productId())
        .setMarket(view.market())
        .setLanguage(view.language())
        .setProduct(product(view.product()))
        .setPricing(pricing(view.pricing()))
        .setAvailability(availability(view.availability()))
        .setPersonalization(personalization(view.personalization()))
        .setMeta(meta(view.meta()))
        .build();
  }

  private static Product product(ProductSection product) {
    Product.Builder proto =
        Product.newBuilder()
            .setName(product.name())
            .setBrand(product.brand())
            .addAllImages(product.images());
    ifPresent(product.description(), proto::setDescription);
    ifPresent(product.category(), proto::setCategory);
    ifPresent(product.ean(), proto::setEan);
    ifPresent(product.salesUnit(), proto::setSalesUnit);
    ifPresent(product.shopUrl(), proto::setShopUrl);
    product
        .specs()
        .forEach(
            spec ->
                proto.addSpecs(Spec.newBuilder().setLabel(spec.label()).setValue(spec.value())));
    return proto.build();
  }

  private static Pricing pricing(PricingSection pricing) {
    Pricing.Builder proto = Pricing.newBuilder().setStatus(pricing.status());
    ifPresent(pricing.currency(), proto::setCurrency);
    ifPresent(pricing.priceType(), proto::setPriceType);
    ifPresent(decimal(pricing.basePrice()), proto::setBasePrice);
    ifPresent(pricing.discountPercent(), proto::setDiscountPercent);
    ifPresent(decimal(pricing.finalPrice()), proto::setFinalPrice);
    ifPresent(pricing.label(), proto::setLabel);
    ifPresent(
        pricing.formatted(),
        formatted ->
            proto.setFormatted(
                Formatted.newBuilder()
                    .setBasePrice(formatted.basePrice())
                    .setFinalPrice(formatted.finalPrice())));
    return proto.build();
  }

  private static Availability availability(AvailabilitySection availability) {
    Availability.Builder proto =
        Availability.newBuilder().setStatus(availability.status()).setLabel(availability.label());
    ifPresent(availability.stockLevel(), proto::setStockLevel);
    ifPresent(
        availability.expectedDelivery(),
        date -> proto.setExpectedDelivery(date.toString())); // ISO-8601, as in JSON
    ifPresent(
        availability.warehouse(),
        warehouse ->
            proto.setWarehouse(
                Warehouse.newBuilder()
                    .setCode(warehouse.code())
                    .setCity(warehouse.city())
                    .setCountry(warehouse.country())));
    return proto.build();
  }

  private static Personalization personalization(PersonalizationSection personalization) {
    Personalization.Builder proto =
        Personalization.newBuilder().setStatus(personalization.status());
    ifPresent(personalization.segment(), proto::setSegment);
    ifPresent(personalization.preferredBrand(), proto::setPreferredBrand);
    return proto.build();
  }

  private static Meta meta(com.kramp.aggregator.presentation.ProductView.Meta meta) {
    Meta.Builder proto =
        Meta.newBuilder().setDegraded(meta.degraded()).setCorrelationId(meta.correlationId());
    meta.sources()
        .forEach(
            (name, report) ->
                proto.addSources(
                    SourceReport.newBuilder()
                        .setName(name)
                        .setStatus(report.status())
                        .setDurationMs(report.durationMs())));
    return proto.build();
  }

  private static String decimal(BigDecimal amount) {
    return amount == null ? null : amount.toPlainString();
  }

  private static <T> void ifPresent(T value, Consumer<T> setter) {
    if (value != null) {
      setter.accept(value);
    }
  }
}
