package com.kramp.aggregator.presentation;

import com.kramp.aggregator.aggregation.AggregationResult;
import com.kramp.aggregator.aggregation.SourceOutcome;
import com.kramp.aggregator.market.Market;
import com.kramp.aggregator.source.availability.AvailabilitySource;
import com.kramp.aggregator.source.availability.StockInfo;
import com.kramp.aggregator.source.catalog.CatalogProduct;
import com.kramp.aggregator.source.catalog.CatalogSource;
import com.kramp.aggregator.source.customer.CustomerProfile;
import com.kramp.aggregator.source.customer.CustomerSource;
import com.kramp.aggregator.source.pricing.PriceQuote;
import com.kramp.aggregator.source.pricing.PricingSource;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the sources' outcomes into the client's view: content in the language the catalog served,
 * labels, prices and dates in the market's language.
 */
public final class ProductViewAssembler {

  private final Labels labels;

  public ProductViewAssembler(Labels labels) {
    this.labels = labels;
  }

  public ProductView assemble(AggregationResult result, String correlationId) {
    Market market = result.request().market();
    if (!(result.<CatalogProduct>outcome(CatalogSource.NAME)
        instanceof SourceOutcome.Ok<CatalogProduct> catalog)) {
      throw new IllegalStateException("an aggregation result always holds the catalog's product");
    }
    CatalogProduct product = catalog.value();
    return new ProductView(
        result.request().productId(),
        market.code(),
        product.language(),
        product(product),
        pricing(result.outcome(PricingSource.NAME), market),
        availability(result.outcome(AvailabilitySource.NAME), market),
        personalization(result.outcome(CustomerSource.NAME), product.brand()),
        meta(result, correlationId));
  }

  private static ProductView.ProductSection product(CatalogProduct product) {
    return new ProductView.ProductSection(
        product.name(),
        product.brand(),
        product.description(),
        product.category(),
        product.ean(),
        product.salesUnit(),
        product.specs().stream()
            .map(spec -> new ProductView.Spec(spec.label(), spec.value()))
            .toList(),
        product.images(),
        product.shopUrl());
  }

  private ProductView.PricingSection pricing(SourceOutcome<PriceQuote> outcome, Market market) {
    return switch (outcome) {
      case SourceOutcome.Ok<PriceQuote> ok -> {
        PriceQuote quote = ok.value();
        yield new ProductView.PricingSection(
            "AVAILABLE",
            quote.currency().getCurrencyCode(),
            quote.priceType(),
            quote.basePrice(),
            quote.discountPercent(),
            quote.finalPrice(),
            new ProductView.Formatted(
                PriceFormatter.format(quote.basePrice(), quote.currency(), market.locale()),
                PriceFormatter.format(quote.finalPrice(), quote.currency(), market.locale())),
            null);
      }
      // Pricing has no price for this product in this market: a business answer.
      case SourceOutcome.NotFound<PriceQuote> notFound ->
          unavailablePrice("pricing.onRequest", market.locale());
      case SourceOutcome.Failed<PriceQuote> failed ->
          unavailablePrice("pricing.unavailable", market.locale());
      case SourceOutcome.Skipped<PriceQuote> skipped ->
          unavailablePrice("pricing.unavailable", market.locale());
    };
  }

  private ProductView.PricingSection unavailablePrice(String labelKey, Locale locale) {
    return new ProductView.PricingSection(
        "UNAVAILABLE", null, null, null, null, null, null, labels.text(labelKey, locale));
  }

  private ProductView.AvailabilitySection availability(
      SourceOutcome<StockInfo> outcome, Market market) {
    if (!(outcome instanceof SourceOutcome.Ok<StockInfo> ok)) {
      return new ProductView.AvailabilitySection(
          "UNKNOWN", null, null, null, labels.text("availability.unknown", market.locale()));
    }
    StockInfo stock = ok.value();
    boolean inStock = stock.stockLevel() > 0;
    String date =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withLocale(market.locale())
            .format(stock.expectedDelivery());
    return new ProductView.AvailabilitySection(
        inStock ? "IN_STOCK" : "OUT_OF_STOCK",
        stock.stockLevel(),
        new ProductView.Warehouse(
            stock.warehouse().code(), stock.warehouse().city(), stock.warehouse().country()),
        stock.expectedDelivery(),
        labels.text(
            inStock ? "availability.inStock" : "availability.outOfStock", market.locale(), date));
  }

  /** The value aggregation adds: the catalog's brand checked against the customer's brands. */
  private static ProductView.PersonalizationSection personalization(
      SourceOutcome<CustomerProfile> outcome, String brand) {
    if (!(outcome instanceof SourceOutcome.Ok<CustomerProfile> ok)) {
      return new ProductView.PersonalizationSection("STANDARD", null, null);
    }
    CustomerProfile profile = ok.value();
    String wanted = brand.toLowerCase(Locale.ROOT);
    boolean preferred =
        profile.preferredBrands().stream()
            .anyMatch(candidate -> candidate.toLowerCase(Locale.ROOT).equals(wanted));
    return new ProductView.PersonalizationSection("PERSONALIZED", profile.segment(), preferred);
  }

  private static ProductView.Meta meta(AggregationResult result, String correlationId) {
    Map<String, ProductView.SourceReport> sources = new LinkedHashMap<>();
    result
        .outcomes()
        .forEach(
            (name, outcome) ->
                sources.put(
                    name,
                    new ProductView.SourceReport(
                        outcome.status().name(), outcome.duration().toMillis())));
    return new ProductView.Meta(
        result.degraded(), correlationId, Collections.unmodifiableMap(sources));
  }
}
