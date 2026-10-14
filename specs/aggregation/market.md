# Market and language

Owns: REQ-AGG-MARKET-*

## What it does
- REQ-AGG-MARKET-001 — When the market code is not configured, the system shall reject the request with 400 UNSUPPORTED_MARKET.
- REQ-AGG-MARKET-002 — The system shall accept market codes case-insensitively.
- REQ-AGG-MARKET-003 — The system shall request catalog content in the market's language.
- REQ-AGG-MARKET-004 — When content is missing in the market language, the response shall report the language actually served.
- REQ-AGG-MARKET-005 — Prices shall be expressed in the market currency and formatted for the market locale.
- REQ-AGG-MARKET-006 — Pricing and availability labels shall be localised to the market language.
- REQ-AGG-MARKET-007 — When the product is outside the market assortment, the system shall respond 404 PRODUCT_NOT_IN_MARKET.
- REQ-AGG-MARKET-008 — A price in a currency other than the market's shall be treated as pricing unavailable.

## Rules & invariants
| Market | Language | Currency |
|---|---|---|
| nl-NL, nl-BE | nl | EUR |
| fr-BE, fr-FR | fr | EUR |
| de-DE, de-AT | de | EUR |
| pl-PL | pl | PLN |
| en-GB | en | GBP |

- Codes are normalised to `ll-CC`; any casing and `_` as separator are accepted.
- Catalog falls back to English when the market language is missing; `language` reports what was served, labels stay in the market language.
- Availability labels show the delivery date formatted for the market locale (`FormatStyle.MEDIUM`), never relative words like "tomorrow".
- A price quoted in another currency is never shown: pricing becomes UNAVAILABLE and the response is degraded.
- Assortment facts from kramp.com: `10112PB` is not sold in `pl-PL`; `TS19100KR` is not sold in `nl-NL`, and is sold in `nl-BE` with English content (fixture assumption).

## Risk controls
None in this leaf.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-AGG-MARKET-001 | automated | AT-MARKET-01 · MarketRegistryTest#shouldRejectWhenMarketNotConfigured, ProductRestApiIT#shouldReturn400WhenMarketUnsupported | ❌ no evidence — GAP |
| REQ-AGG-MARKET-002 | automated | AT-MARKET-02 · MarketRegistryTest#shouldNormalizeWhenCodeHasOddCasingOrSeparator | ❌ no evidence — GAP |
| REQ-AGG-MARKET-003 | automated | AT-MARKET-03 · CatalogSourceTest#shouldRequestMarketLanguageWhenFetching | ❌ no evidence — GAP |
| REQ-AGG-MARKET-004 | automated | AT-MARKET-04 · ProductRestApiIT#shouldReportServedLanguageWhenCatalogFallsBack | ❌ no evidence — GAP |
| REQ-AGG-MARKET-005 | automated | AT-MARKET-05 · PriceFormatterTest#shouldFormatForMarketLocaleWhenFormatting | ❌ no evidence — GAP |
| REQ-AGG-MARKET-006 | automated | AT-MARKET-06 · LabelsTest#shouldTranslateEveryKeyWhenMarketLanguageRequested | ❌ no evidence — GAP |
| REQ-AGG-MARKET-007 | automated | AT-MARKET-07 · ProductRestApiIT#shouldReturn404WhenProductOutsideMarketAssortment | ❌ no evidence — GAP |
| REQ-AGG-MARKET-008 | automated | AT-ROB-04 · PricingSourceTest#shouldFailAsUnavailableWhenCurrencyDoesNotMatchMarket | ❌ no evidence — GAP |

## Where the code lives
`aggregator/src/main/java/com/kramp/aggregator/market/`, `…/presentation/`, `…/source/pricing/`, `aggregator/src/main/resources/messages*.properties`.

## Connected
UR-2.
