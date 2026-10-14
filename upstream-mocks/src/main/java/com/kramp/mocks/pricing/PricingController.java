package com.kramp.mocks.pricing;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.kramp.mocks.behavior.Upstream;
import com.kramp.mocks.errors.ErrorShapes;
import com.kramp.mocks.fixtures.MockMarket;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/pricing")
class PricingController {

  private final PricingLookup lookup;

  PricingController(PricingLookup lookup) {
    this.lookup = lookup;
  }

  @GetMapping("/prices/{id}")
  ResponseEntity<?> price(
      @PathVariable String id,
      @RequestParam String market,
      @RequestParam(required = false) String customerId) {
    Optional<MockMarket> resolved = MockMarket.fromCode(market);
    if (resolved.isEmpty()) {
      return ErrorShapes.response(
          Upstream.PRICING, BAD_REQUEST, "UNKNOWN_MARKET", "unknown market");
    }
    return lookup
        .quote(id, resolved.get(), customerId)
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(
            () ->
                ErrorShapes.response(
                    Upstream.PRICING, NOT_FOUND, "NO_PRICE", "no price for this market"));
  }
}
