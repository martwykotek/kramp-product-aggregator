package com.kramp.aggregator.presentation;

/** Why a request got no product view; REST and gRPC each map it to their own status. */
public enum ErrorCode {
  INVALID_REQUEST,
  UNSUPPORTED_MARKET,
  PRODUCT_NOT_FOUND,
  PRODUCT_NOT_IN_MARKET,
  CATALOG_UNAVAILABLE,
  CATALOG_TIMEOUT
}
