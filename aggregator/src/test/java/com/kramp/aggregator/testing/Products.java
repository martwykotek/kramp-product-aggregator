package com.kramp.aggregator.testing;

import com.kramp.aggregator.source.availability.StockInfo;
import com.kramp.aggregator.source.catalog.CatalogProduct;
import com.kramp.aggregator.source.customer.CustomerProfile;
import com.kramp.aggregator.source.pricing.PriceQuote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;

/** What the four sources answer for W6103 · pl-PL · C-1002 (test specification 3.2 and 3.7). */
public final class Products {

  public static final CatalogProduct W6103_PL = catalog("MANN-FILTER");

  public static final PriceQuote W6103_PLN_C1002 =
      new PriceQuote(
          Currency.getInstance("PLN"), new BigDecimal("64.90"), 12, new BigDecimal("57.11"), "NET");

  public static final StockInfo W6103_PL_MOD =
      new StockInfo(
          42,
          new StockInfo.Warehouse("PL-MOD", "Modła Królewska", "PL"),
          LocalDate.of(2026, 10, 15));

  public static final CustomerProfile C1002 =
      new CustomerProfile("WORKSHOP", List.of("MANN-FILTER", "SKF"));

  private Products() {}

  /** W6103 in Polish, carrying the given brand. */
  public static CatalogProduct catalog(String brand) {
    return new CatalogProduct(
        "W6103",
        brand,
        "4011558738402",
        "EA",
        "Filtr oleju Mann & Hummel",
        "Wkręcany filtr oleju MANN-FILTER W 610/3.",
        "Filtry wymienne",
        List.of(new CatalogProduct.Spec("Gwint montażowy", "M20 x 1.50 mm (metric)")),
        List.of("https://images.kramp.com/images/60/08/w6103.jpg"),
        "https://www.kramp.com/shop-pl/pl/p/filtr-oleju-mann-hummel--W6103",
        "pl");
  }
}
