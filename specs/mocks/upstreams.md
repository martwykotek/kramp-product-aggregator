# Upstream mocks

Owns: REQ-MOCK-UPSTREAM-*

## What it does
- REQ-MOCK-UPSTREAM-001 — Each mock shall delay responses log-normally with the median at its stated typical latency.
- REQ-MOCK-UPSTREAM-002 — Each mock shall fail with probability one minus its stated reliability.
- REQ-MOCK-UPSTREAM-003 — Mock failures shall split into fast 503, slow 500 and hang in configured proportions.
- REQ-MOCK-UPSTREAM-004 — Given the same seed, mocks shall reproduce the same latency and failure sequence.
- REQ-MOCK-UPSTREAM-005 — The catalog mock shall serve the 15 researched Kramp products with their kramp.com names.
- REQ-MOCK-UPSTREAM-006 — Availability shall promise next-working-day delivery for orders before 18:00 market local time.
- REQ-MOCK-UPSTREAM-007 — When the local warehouse lacks stock, availability shall ship from Varsseveld one working day later.
- REQ-MOCK-UPSTREAM-008 — Pricing shall quote in the market currency and apply the customer's tier discount.

## Rules & invariants
| Mock | Median | Reliability |
|---|---|---|
| catalog | 50 ms | 99.9% |
| pricing | 80 ms | 99.5% |
| availability | 100 ms | 98% |
| customer | 60 ms | 99% |

- Latency: log-normal, σ = ln(3)/2.326 so p99 ≈ 3 × median.
- Failure mix: 50% fast 503 (~5 ms), 30% 500 after normal latency, 20% hang 2–5 s then 503. Each mock reports errors in its own JSON shape.
- Warehouses (real locations): Varsseveld NL (central), Strullendorf DE, Poitiers FR, Biggleswade GB, Modła Królewska PL. Out of stock everywhere → restock date 10 working days out.
- Product data is copied from kramp.com; prices, stock, customers and discounts are fictitious.

## Risk controls
None in this leaf.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-MOCK-UPSTREAM-001 | automated | AT-MOCK-01 · LatencyModelTest#shouldCentreOnMedianWithLongTailWhenSampledManyTimes | ❌ no evidence — GAP |
| REQ-MOCK-UPSTREAM-002 | automated | AT-MOCK-02 · UpstreamBehaviorTest#shouldFailAtOneMinusReliabilityWhenHealthy | ❌ no evidence — GAP |
| REQ-MOCK-UPSTREAM-003 | automated | AT-MOCK-03 · UpstreamBehaviorTest#shouldSplitFailuresByConfiguredMixWhenFailing | ❌ no evidence — GAP |
| REQ-MOCK-UPSTREAM-004 | automated | AT-MOCK-04 · UpstreamBehaviorTest#shouldReproduceSequenceWhenSeedIsTheSame | ❌ no evidence — GAP |
| REQ-MOCK-UPSTREAM-005 | automated | AT-MOCK-05 · CatalogFixturesTest#shouldContainExactlyTheResearchedKrampProductsWhenLoaded | ❌ no evidence — GAP |
| REQ-MOCK-UPSTREAM-006 | automated | AT-MOCK-06 · DeliveryDateCalculatorTest#shouldPromiseNextWorkingDayWhenOrderedBeforeCutoff | ❌ no evidence — GAP |
| REQ-MOCK-UPSTREAM-007 | automated | AT-MOCK-07 · AvailabilityLookupTest#shouldShipFromVarsseveldOneDayLaterWhenLocalWarehouseIsEmpty | ❌ no evidence — GAP |
| REQ-MOCK-UPSTREAM-008 | automated | AT-MOCK-08 · PricingLookupTest#shouldQuoteMarketCurrencyWithCustomerDiscountWhenCustomerKnown | ❌ no evidence — GAP |

## Where the code lives
`upstream-mocks/src/main/java/com/kramp/mocks/`, `upstream-mocks/src/main/resources/fixtures/`.

## Connected
UR-8, UR-2.
