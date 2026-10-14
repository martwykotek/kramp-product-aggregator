package com.kramp.mocks.customer;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.kramp.mocks.behavior.Upstream;
import com.kramp.mocks.errors.ErrorShapes;
import com.kramp.mocks.fixtures.Fixtures;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class CustomerController {

  private final Fixtures fixtures;

  CustomerController(Fixtures fixtures) {
    this.fixtures = fixtures;
  }

  @GetMapping("/customers/{id}")
  ResponseEntity<?> customer(@PathVariable String id) {
    return fixtures
        .customer(id)
        .<ResponseEntity<?>>map(
            c ->
                ResponseEntity.ok(
                    new CustomerResponse(c.customerId(), c.segment(), c.preferredBrands())))
        .orElseGet(
            () ->
                ErrorShapes.response(Upstream.CUSTOMER, NOT_FOUND, "UNKNOWN", "unknown customer"));
  }
}
