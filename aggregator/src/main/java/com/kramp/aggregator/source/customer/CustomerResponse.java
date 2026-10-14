package com.kramp.aggregator.source.customer;

import java.util.List;

/** The customer service's wire shape; every field may be missing until checked. */
record CustomerResponse(String segment, List<String> preferredBrands) {}
