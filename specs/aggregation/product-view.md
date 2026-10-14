# Product view — what one call returns

Owns: REQ-AGG-VIEW-*

## What it does
- REQ-AGG-VIEW-001 — When a supported market is requested, the system shall return the product's catalog details.
- REQ-AGG-VIEW-002 — The response shall combine product, pricing, availability and personalisation sections in one payload.
- REQ-AGG-VIEW-003 — When a customer ID is given, the system shall request pricing for that customer.
- REQ-AGG-VIEW-004 — The system shall call all applicable upstreams concurrently.
- REQ-AGG-VIEW-005 — When no customer ID is given, the system shall not call Customer and return STANDARD personalisation.
- REQ-AGG-VIEW-006 — When the product brand is a customer's preferred brand, personalisation shall set `preferredBrand` true.
- REQ-AGG-VIEW-007 — A newly registered optional data source shall be called and reported with the same timeout and fallback rules.

## Rules & invariants
- Sources: catalog (REQUIRED); pricing, availability, customer (OPTIONAL). A new source is a `ProductDataSource` bean plus an `aggregator.sources.<name>` block; orchestration does not change.
- `meta.sources` lists every source with `status` and `durationMs`, in configuration order (catalog, pricing, availability, customer).
- `preferredBrand` compares the catalog brand with the customer's preferred brands, ignoring case; it is `null` for STANDARD.

## Risk controls
None in this leaf.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-AGG-VIEW-001 | automated | AT-VIEW-01 · ProductRestApiIT#shouldReturnAggregatedViewWhenAllUpstreamsAnswer | ❌ no evidence — GAP |
| REQ-AGG-VIEW-002 | automated | AT-VIEW-02 · ProductRestApiIT#shouldReturnAggregatedViewWhenAllUpstreamsAnswer | ❌ no evidence — GAP |
| REQ-AGG-VIEW-003 | automated | AT-VIEW-03 · PricingSourceTest#shouldSendCustomerIdWhenCustomerGiven, #shouldOmitCustomerIdWhenNoCustomer | ✅ |
| REQ-AGG-VIEW-004 | automated | AT-VIEW-04 · ProductAggregationServiceTest#shouldCallSourcesConcurrentlyWhenAllApplicable | ✅ |
| REQ-AGG-VIEW-005 | automated | AT-VIEW-05 · ProductRestApiIT#shouldSkipCustomerWhenHeaderMissing, ProductAggregationServiceTest#shouldNotInvokeSourceWhenNotApplicable | ❌ no evidence — GAP |
| REQ-AGG-VIEW-006 | automated | AT-VIEW-06 · ProductViewAssemblerTest#shouldFlagPreferredBrandWhenBrandMatches | ❌ no evidence — GAP |
| REQ-AGG-VIEW-007 | automated | AT-VIEW-07 · ExtensibilityIT#shouldAggregateNewSourceWhenRegisteredAsBean | ❌ no evidence — GAP |

## Where the code lives
`aggregator/src/main/java/com/kramp/aggregator/aggregation/`, `…/presentation/ProductViewAssembler.java`.

## Connected
UR-1, UR-3, UR-5, UR-6 in `.changes/CR-001_product-aggregator.md`; `aggregation/resilience.md`.
