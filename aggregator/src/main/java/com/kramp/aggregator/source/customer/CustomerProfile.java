package com.kramp.aggregator.source.customer;

import java.util.List;

public record CustomerProfile(String segment, List<String> preferredBrands) {}
