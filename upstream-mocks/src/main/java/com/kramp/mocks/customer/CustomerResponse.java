package com.kramp.mocks.customer;

import java.util.List;

public record CustomerResponse(String customerId, String segment, List<String> preferredBrands) {}
