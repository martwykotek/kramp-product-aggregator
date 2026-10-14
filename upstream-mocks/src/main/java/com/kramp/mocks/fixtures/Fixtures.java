package com.kramp.mocks.fixtures;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Loads the fixture files once at startup. */
@Component
public class Fixtures {

  private final Map<String, ProductFixture> products;
  private final Map<String, Map<String, String>> specLabels;
  private final Map<String, Map<String, BigDecimal>> prices;
  private final Map<String, Integer> discounts;
  private final Map<String, CustomerFixture> customers;
  private final Map<String, StockFixture> stock;

  public Fixtures(JsonMapper mapper) {
    this.products =
        read(mapper, "products.json", new TypeReference<List<ProductFixture>>() {}).stream()
            .collect(Collectors.toMap(ProductFixture::id, Function.identity()));
    this.specLabels = read(mapper, "spec-labels.json", new TypeReference<>() {});
    this.prices = read(mapper, "prices.json", new TypeReference<>() {});
    this.discounts = read(mapper, "discounts.json", new TypeReference<>() {});
    this.customers =
        read(mapper, "customers.json", new TypeReference<List<CustomerFixture>>() {}).stream()
            .collect(Collectors.toMap(CustomerFixture::customerId, Function.identity()));
    this.stock =
        read(mapper, "stock.json", new TypeReference<List<StockFixture>>() {}).stream()
            .collect(Collectors.toMap(StockFixture::productId, Function.identity()));
  }

  private static <T> T read(JsonMapper mapper, String file, TypeReference<T> type) {
    try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + file)) {
      if (in == null) {
        throw new IllegalStateException("Missing fixture file " + file);
      }
      return mapper.readValue(in, type);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public Set<String> productIds() {
    return products.keySet();
  }

  public Optional<ProductFixture> product(String id) {
    return Optional.ofNullable(products.get(id));
  }

  public String specLabel(String key, String language) {
    Map<String, String> labels = specLabels.get(key);
    if (labels == null || !labels.containsKey(language)) {
      throw new IllegalStateException("No label for spec '" + key + "' in " + language);
    }
    return labels.get(language);
  }

  public Optional<BigDecimal> price(String productId, String currency) {
    return Optional.ofNullable(prices.getOrDefault(productId, Map.of()).get(currency));
  }

  /** Discount agreed with the customer; customers without an agreement pay list price. */
  public int discount(String customerId) {
    return discounts.getOrDefault(customerId, 0);
  }

  public Optional<CustomerFixture> customer(String id) {
    return Optional.ofNullable(customers.get(id));
  }

  public Optional<StockFixture> stock(String productId) {
    return Optional.ofNullable(stock.get(productId));
  }
}
