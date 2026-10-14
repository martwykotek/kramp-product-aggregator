package com.kramp.aggregator.aggregation;

import com.kramp.aggregator.market.Market;
import java.util.Optional;

public record ProductRequest(String productId, Market market, Optional<String> customerId) {}
