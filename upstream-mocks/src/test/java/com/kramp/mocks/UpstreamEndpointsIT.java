package com.kramp.mocks;

import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UpstreamEndpointsIT {

  @Autowired MockMvc mvc;

  @Nested
  class Catalog {

    @Test
    void shouldServeLocalizedProductWhenLanguageExists() throws Exception {
      // Given / When / Then
      mvc.perform(get("/catalog/products/W6103").param("market", "pl-PL").param("lang", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("W6103"))
          .andExpect(jsonPath("$.brand").value("MANN-FILTER"))
          .andExpect(jsonPath("$.name").value("Filtr oleju Mann & Hummel"))
          .andExpect(jsonPath("$.category").value("Filtry wymienne"))
          .andExpect(jsonPath("$.language").value("pl"))
          .andExpect(jsonPath("$.specs[0].label").value("Gwint montażowy"))
          .andExpect(
              jsonPath("$.images[0]").value("https://images.kramp.com/images/60/08/w6103.jpg"))
          .andExpect(
              jsonPath("$.url")
                  .value("https://www.kramp.com/shop-pl/pl/p/filtr-oleju-mann-hummel--W6103"));
    }

    @Test
    void shouldServeProductWithoutEanWhenResearchShowsNone() throws Exception {
      // Given / When / Then
      mvc.perform(get("/catalog/products/FFH121GASF").param("market", "de-DE").param("lang", "de"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.ean").value(nullValue()))
          .andExpect(jsonPath("$.name").value("Schnellkupplungsbu. 3/4-1\" BS"));
    }

    @Test
    void shouldFallBackToEnglishWhenLanguageMissing() throws Exception {
      // Given / When / Then
      mvc.perform(get("/catalog/products/TS19100KR").param("market", "nl-BE").param("lang", "nl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.language").value("en"))
          .andExpect(jsonPath("$.name").value("Seat top black PVC"));
    }

    @Test
    void shouldAnswerNotInAssortmentWhenMarketExcludesProduct() throws Exception {
      // Given / When / Then
      mvc.perform(get("/catalog/products/10112PB").param("market", "pl-PL").param("lang", "pl"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("NOT_IN_ASSORTMENT"));
    }

    @Test
    void shouldAnswerProductNotFoundWhenProductUnknown() throws Exception {
      // Given / When / Then
      mvc.perform(get("/catalog/products/NOPE").param("market", "pl-PL").param("lang", "pl"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.error.code").value("PRODUCT_NOT_FOUND"));
    }
  }

  @Nested
  class Pricing {

    @Test
    void shouldQuoteDiscountedPriceWhenCustomerKnown() throws Exception {
      // Given / When / Then
      mvc.perform(
              get("/pricing/prices/W6103").param("market", "pl-PL").param("customerId", "C-1002"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.productId").value("W6103"))
          .andExpect(jsonPath("$.currency").value("PLN"))
          .andExpect(jsonPath("$.basePrice").value(64.90))
          .andExpect(jsonPath("$.discountPercent").value(12))
          .andExpect(jsonPath("$.finalPrice").value(57.11))
          .andExpect(jsonPath("$.priceType").value("NET"));
    }

    @Test
    void shouldAnswerInPricingErrorShapeWhenNoPrice() throws Exception {
      // Given / When / Then
      mvc.perform(get("/pricing/prices/NOPE").param("market", "pl-PL"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.status").value(404))
          .andExpect(jsonPath("$.reason").value("no price for this market"));
    }
  }

  @Nested
  class Availability {

    @Test
    void shouldReportLocalStockWhenInStockLocally() throws Exception {
      // Given / When / Then
      mvc.perform(get("/availability/stock/W6103").param("market", "pl-PL"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.productId").value("W6103"))
          .andExpect(jsonPath("$.stockLevel").value(42))
          .andExpect(jsonPath("$.warehouse.code").value("PL-MOD"))
          .andExpect(jsonPath("$.warehouse.city").value("Modła Królewska"))
          .andExpect(jsonPath("$.warehouse.country").value("PL"))
          .andExpect(jsonPath("$.expectedDelivery").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}")));
    }

    @Test
    void shouldAnswerInAvailabilityErrorShapeWhenProductUnknown() throws Exception {
      // Given / When / Then
      mvc.perform(get("/availability/stock/NOPE").param("market", "pl-PL"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.errorCode").value("AV-404"));
    }
  }

  @Nested
  class Customer {

    @Test
    void shouldServeProfileWhenCustomerKnown() throws Exception {
      // Given / When / Then
      mvc.perform(get("/customers/C-1002"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.customerId").value("C-1002"))
          .andExpect(jsonPath("$.segment").value("WORKSHOP"))
          .andExpect(jsonPath("$.preferredBrands[0]").value("MANN-FILTER"));
    }

    @Test
    void shouldAnswerPlainTextWhenCustomerUnknown() throws Exception {
      // Given / When / Then
      mvc.perform(get("/customers/C-9999"))
          .andExpect(status().isNotFound())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
          .andExpect(content().string("Not Found"));
    }
  }
}
