package com.kramp.mocks.availability;

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
@RequestMapping("/availability")
class AvailabilityController {

  private final AvailabilityLookup lookup;

  AvailabilityController(AvailabilityLookup lookup) {
    this.lookup = lookup;
  }

  @GetMapping("/stock/{id}")
  ResponseEntity<?> stock(@PathVariable String id, @RequestParam String market) {
    Optional<MockMarket> resolved = MockMarket.fromCode(market);
    if (resolved.isEmpty()) {
      return ErrorShapes.response(
          Upstream.AVAILABILITY, BAD_REQUEST, "UNKNOWN_MARKET", "unknown market");
    }
    return lookup
        .stock(id, resolved.get())
        .<ResponseEntity<?>>map(ResponseEntity::ok)
        .orElseGet(
            () ->
                ErrorShapes.response(
                    Upstream.AVAILABILITY, NOT_FOUND, "UNKNOWN_PRODUCT", "unknown product"));
  }
}
