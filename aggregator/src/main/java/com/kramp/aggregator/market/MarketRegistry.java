package com.kramp.aggregator.market;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The configured markets, looked up by a code as a client may type it. */
public final class MarketRegistry {

  private final Map<String, Market> markets = new LinkedHashMap<>();

  public MarketRegistry(List<Market> markets) {
    markets.forEach(market -> this.markets.put(market.code(), market));
  }

  /** Accepts any casing and `_` as separator; anything else not configured is unsupported. */
  public Market resolve(String requested) {
    Market market = normalise(requested).map(markets::get).orElse(null);
    if (market == null) {
      throw new UnsupportedMarketException(requested, supportedCodes());
    }
    return market;
  }

  /** In configuration order. */
  public List<String> supportedCodes() {
    return List.copyOf(markets.keySet());
  }

  private static Optional<String> normalise(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    String[] parts = raw.strip().replace('_', '-').split("-", -1);
    if (parts.length != 2 || parts[0].length() != 2 || parts[1].length() != 2) {
      return Optional.empty();
    }
    return Optional.of(parts[0].toLowerCase(Locale.ROOT) + "-" + parts[1].toUpperCase(Locale.ROOT));
  }
}
