package com.kramp.aggregator.api.rest;

import com.kramp.aggregator.presentation.ProductView;
import com.kramp.aggregator.presentation.ProductViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The customer id travels in a header so it stays out of URLs, access logs and caches; a
 * `customerId` query parameter is not part of the contract and is ignored.
 */
@RestController
@RequestMapping("/api/v1/products")
class ProductController {

  private final ProductViewService views;

  ProductController(ProductViewService views) {
    this.views = views;
  }

  @GetMapping("/{productId}")
  ProductView product(
      @PathVariable String productId,
      @RequestParam String market,
      @RequestHeader(name = "X-Customer-Id", required = false) String customerId) {
    return views.view(productId, market, customerId);
  }
}
