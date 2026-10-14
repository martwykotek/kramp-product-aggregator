package com.kramp.aggregator.testing;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * WireMock stubs that answer in each upstream's own shape, as the upstream mocks do (test
 * specification 3.7). Every helper returns a mapping for the test to register.
 */
public final class UpstreamStubs {

  public static final String CATALOG = "/catalog/products/.*";
  public static final String PRICING = "/pricing/prices/.*";
  public static final String AVAILABILITY = "/availability/stock/.*";
  public static final String CUSTOMERS = "/customers/.*";

  private UpstreamStubs() {}

  /** W6103 content; `id` and `lang` name the product and the language the catalog served. */
  public static MappingBuilder catalogOk(String id, String lang) {
    return get(urlPathEqualTo("/catalog/products/" + id)).willReturn(okJson(catalogBody(id, lang)));
  }

  public static String catalogBody(String id, String lang) {
    return """
        {"id":"%s","brand":"MANN-FILTER","ean":"4011558738402","salesUnit":"EA",\
        "name":"Filtr oleju Mann & Hummel",\
        "description":"Wkręcany filtr oleju MANN-FILTER W 610/3 z gwintem M20x1,5, wkładem \
        celulozowym 21 µm, zaworem obejściowym i zaworem zwrotnym.",\
        "category":"Filtry wymienne",\
        "specs":[{"label":"Gwint montażowy","value":"M20 x 1.50 mm (metric)"}],\
        "images":["https://images.kramp.com/images/60/08/w6103.jpg"],\
        "url":"https://www.kramp.com/shop-pl/pl/p/filtr-oleju-mann-hummel--W6103",\
        "language":"%s"}"""
        .formatted(id, lang);
  }

  /** `PRODUCT_NOT_FOUND` or `NOT_IN_ASSORTMENT`, in the catalog's error shape. */
  public static MappingBuilder catalogNotFound(String code) {
    return get(urlPathMatching(CATALOG))
        .willReturn(
            aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody(
                    "{\"error\":{\"code\":\"%s\",\"message\":\"not found\"}}".formatted(code)));
  }

  public static MappingBuilder pricingOk(
      String currency, double basePrice, int discountPercent, double finalPrice) {
    return get(urlPathMatching(PRICING))
        .willReturn(
            okJson(
                """
                {"productId":"W6103","currency":"%s","basePrice":%s,"discountPercent":%d,\
                "finalPrice":%s,"priceType":"NET"}"""
                    .formatted(currency, money(basePrice), discountPercent, money(finalPrice))));
  }

  public static MappingBuilder pricingNotFound() {
    return get(urlPathMatching(PRICING))
        .willReturn(
            aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"status\":404,\"reason\":\"no price for this market\"}"));
  }

  public static MappingBuilder availabilityOk(
      int stockLevel, String code, String city, String country, String expectedDelivery) {
    return get(urlPathMatching(AVAILABILITY))
        .willReturn(
            okJson(
                """
                {"productId":"W6103","stockLevel":%d,\
                "warehouse":{"code":"%s","city":"%s","country":"%s"},\
                "expectedDelivery":"%s"}"""
                    .formatted(stockLevel, code, city, country, expectedDelivery)));
  }

  public static MappingBuilder customerOk(String id, String segment, List<String> brands) {
    String preferred =
        brands.stream().map(brand -> "\"" + brand + "\"").collect(Collectors.joining(","));
    return get(urlPathEqualTo("/customers/" + id))
        .willReturn(
            okJson(
                "{\"customerId\":\"%s\",\"segment\":\"%s\",\"preferredBrands\":[%s]}"
                    .formatted(id, segment, preferred)));
  }

  /** The customer service answers unknown ids with a bare text 404. */
  public static MappingBuilder customerNotFound() {
    return get(urlPathMatching(CUSTOMERS))
        .willReturn(
            aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "text/plain")
                .withBody("Not Found"));
  }

  public static MappingBuilder serverError(String pathRegex) {
    return get(urlPathMatching(pathRegex)).willReturn(aResponse().withStatus(500));
  }

  public static MappingBuilder unavailable(String pathRegex) {
    return get(urlPathMatching(pathRegex)).willReturn(aResponse().withStatus(503));
  }

  /** The answer comes after `millis`; every caller waits longer than any budget allows. */
  public static MappingBuilder slow(String pathRegex, int millis) {
    return get(urlPathMatching(pathRegex)).willReturn(okJson("{}").withFixedDelay(millis));
  }

  public static MappingBuilder emptyBody(String pathRegex) {
    return get(urlPathMatching(pathRegex)).willReturn(aResponse().withStatus(200));
  }

  public static MappingBuilder malformed(String pathRegex) {
    return get(urlPathMatching(pathRegex)).willReturn(okJson("{not json"));
  }

  public static MappingBuilder connectionReset(String pathRegex) {
    return get(urlPathMatching(pathRegex))
        .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));
  }

  private static String money(double amount) {
    return BigDecimal.valueOf(amount).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
  }
}
