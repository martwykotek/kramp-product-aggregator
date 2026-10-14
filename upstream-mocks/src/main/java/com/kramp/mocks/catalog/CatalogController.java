package com.kramp.mocks.catalog;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.kramp.mocks.behavior.Upstream;
import com.kramp.mocks.errors.ErrorShapes;
import com.kramp.mocks.fixtures.Fixtures;
import com.kramp.mocks.fixtures.LocalizedContent;
import com.kramp.mocks.fixtures.MockMarket;
import com.kramp.mocks.fixtures.ProductFixture;
import java.util.List;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/catalog")
class CatalogController {

  static final String FALLBACK_LANGUAGE = "en";

  private final Fixtures fixtures;

  CatalogController(Fixtures fixtures) {
    this.fixtures = fixtures;
  }

  @GetMapping("/products/{id}")
  ResponseEntity<?> product(
      @PathVariable String id, @RequestParam String market, @RequestParam String lang) {
    if (MockMarket.fromCode(market).isEmpty()) {
      return ErrorShapes.response(
          Upstream.CATALOG, BAD_REQUEST, "UNKNOWN_MARKET", "unknown market");
    }
    Optional<ProductFixture> found = fixtures.product(id);
    if (found.isEmpty()) {
      return ErrorShapes.response(
          Upstream.CATALOG, NOT_FOUND, "PRODUCT_NOT_FOUND", "unknown product");
    }
    ProductFixture product = found.get();
    if (product.notSoldIn().contains(market)) {
      return ErrorShapes.response(
          Upstream.CATALOG, NOT_FOUND, "NOT_IN_ASSORTMENT", "not sold in this market");
    }
    String served = product.content().containsKey(lang) ? lang : FALLBACK_LANGUAGE;
    LocalizedContent content = product.content().get(served);
    List<CatalogResponse.Spec> specs =
        product.specs().stream()
            .map(s -> new CatalogResponse.Spec(fixtures.specLabel(s.key(), served), s.value()))
            .toList();
    return ResponseEntity.ok(
        new CatalogResponse(
            product.id(),
            product.brand(),
            product.ean(),
            product.salesUnit(),
            content.name(),
            content.description(),
            content.category(),
            specs,
            product.images(),
            content.url(),
            served));
  }
}
