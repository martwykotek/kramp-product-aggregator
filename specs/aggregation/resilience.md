# Resilience — partial failure, budgets, retry, circuit breaking

Owns: REQ-AGG-RES-*, RC-AGG-RES-*

## What it does
- REQ-AGG-RES-001 — When Catalog fails or its circuit is open, the system shall respond 503 CATALOG_UNAVAILABLE.
- REQ-AGG-RES-002 — When Catalog exceeds its budget, the system shall respond 504 CATALOG_TIMEOUT.
- REQ-AGG-RES-003 — When Catalog reports an unknown product, the system shall respond 404 PRODUCT_NOT_FOUND.
- REQ-AGG-RES-004 — When Catalog fails, the system shall respond without waiting for other upstreams.
- REQ-AGG-RES-005 — When Pricing fails or times out, the system shall return the product with pricing UNAVAILABLE.
- REQ-AGG-RES-006 — When Availability fails or times out, the system shall return the product with availability UNKNOWN.
- REQ-AGG-RES-007 — When Customer fails or times out, the system shall return STANDARD personalisation.
- REQ-AGG-RES-008 — When a called optional upstream fails, the response shall set `meta.degraded` true.
- REQ-AGG-RES-009 — A not-found answer from an optional upstream shall not mark the response degraded.
- REQ-AGG-RES-010 — Each upstream call shall end within its configured budget.
- REQ-AGG-RES-011 — The system shall complete aggregation within 500 ms regardless of upstream latency.
- REQ-AGG-RES-012 — After a fast upstream failure, the system shall retry once only if the remaining budget allows.
- REQ-AGG-RES-013 — The system shall not retry an upstream call after a timeout or not-found answer.
- REQ-AGG-RES-014 — When an upstream's failure rate reaches 50% of the last 20 calls, the system shall stop calling it.
- REQ-AGG-RES-015 — While an upstream circuit is open, the system shall apply that upstream's fallback without calling it.
- REQ-AGG-RES-016 — After 10 s open, the system shall allow 3 trial calls and resume when they succeed.
- REQ-AGG-RES-017 — Upstream not-found answers shall not count as failures for circuit breaking.
- REQ-AGG-RES-018 — When timeout budgets are invalid, the application shall refuse to start.
- REQ-AGG-RES-019 — An upstream answer without a valid body shall be treated as that upstream's failure.

## Rules & invariants
| Source | Typical latency | Budget | Policy |
|---|---|---|---|
| catalog | 50 ms | 250 ms | REQUIRED |
| pricing | 80 ms | 300 ms | OPTIONAL |
| availability | 100 ms | 350 ms | OPTIONAL |
| customer | 60 ms | 200 ms | OPTIONAL |
| global deadline | — | 500 ms | safety net |

- A budget covers all attempts of one call. Each attempt is bounded by the HTTP read timeout (= budget) and by the orchestrator, which waits budget + 25 ms grace (never past the global deadline) so the client's own timeout fires first and is recorded by the breaker. Every budget therefore leaves room for the grace: budget + 25 ms ≤ global deadline, otherwise the application refuses to start (REQ-AGG-RES-018).
- Retry: at most one, only after a fast failure (a 5xx answer) and only while the remaining budget is at least the source's typical latency. Never after a timeout, a not-found answer or an unusable body. A connection refused or reset before any answer is resent once by the JDK HTTP client itself, so the aggregator does not retry it again: every failure costs at most two upstream calls.
- Circuit breaker per source: count-based window 20, minimum 10 calls, failure-rate threshold 50%, open 10 s, 3 half-open trial calls; the state is decided after the third trial (two or more failures re-open it). Every attempt, retries included, counts.
- An attempt the aggregator interrupts after the source's budget is spent counts as that source's timeout (for example a retry that started with a full read timeout). An attempt interrupted earlier was cancelled because the request was already decided: it releases its breaker permission, is not recorded and is reported as CANCELLED.

| Source | Ok | Not found | Failed (timeout / error / circuit open) | Skipped |
|---|---|---|---|---|
| catalog | product section | 404 PRODUCT_NOT_FOUND / PRODUCT_NOT_IN_MARKET | 504 (timeout) / 503 (error, circuit open) | never |
| pricing | AVAILABLE | UNAVAILABLE "price on request", not degraded | UNAVAILABLE "temporarily unavailable", degraded | never |
| availability | IN_STOCK / OUT_OF_STOCK | UNKNOWN, not degraded | UNKNOWN, degraded | never |
| customer | PERSONALIZED | STANDARD, not degraded | STANDARD, degraded | STANDARD (no header) |

## Risk controls
- RC-AGG-RES-001 — Pricing shall stay the source of truth for discounts when Customer is unavailable.
- RC-AGG-RES-002 — Calls the aggregator cancels shall not count as upstream failures for circuit breaking.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-AGG-RES-001 | automated | AT-RES-01 · ProductRestApiIT#shouldReturn503WhenCatalogFails | ✅ |
| REQ-AGG-RES-002 | automated | AT-RES-02 · ProductRestApiIT#shouldReturn504WhenCatalogTooSlow | ✅ |
| REQ-AGG-RES-003 | automated | AT-RES-03 · ProductRestApiIT#shouldReturn404WhenProductUnknown | ✅ |
| REQ-AGG-RES-004 | automated | AT-RES-04 · ProductAggregationServiceTest#shouldFailFastWhenRequiredSourceFails | ✅ |
| REQ-AGG-RES-005 | automated | AT-RES-05 · ProductRestApiIT#shouldShowProductWithUnavailablePriceWhenPricingFails | ✅ |
| REQ-AGG-RES-006 | automated | AT-RES-06 · ProductRestApiIT#shouldShowUnknownStockWhenAvailabilityFails | ✅ |
| REQ-AGG-RES-007 | automated | AT-RES-07 · ProductRestApiIT#shouldFallBackToStandardWhenCustomerFails | ✅ |
| REQ-AGG-RES-008 | automated | AT-RES-08 · ProductAggregationServiceTest#shouldMarkDegradedWhenAnyCalledOptionalSourceFails | ✅ |
| REQ-AGG-RES-009 | automated | AT-RES-09 · ProductAggregationServiceTest#shouldNotMarkDegradedWhenOptionalSourceReportsNotFound, ProductRestApiIT#shouldTreatUnknownCustomerAsStandardWhenCustomerNotFound | ✅ |
| REQ-AGG-RES-010 | automated | AT-RES-10 · UpstreamTimeoutsTest#shouldEndCallWithinBudgetWhenUpstreamHangs | ✅ |
| REQ-AGG-RES-011 | automated | AT-RES-11 · ProductAggregationServiceTest#shouldFinishWithinGlobalDeadlineWhenAllSourcesHang | ✅ |
| REQ-AGG-RES-012 | automated | AT-RES-12 · GuardedSourceTest#shouldRetryOnceWhenFastFailureLeavesBudget, #shouldNotRetryWhenRemainingBudgetBelowTypicalLatency | ✅ |
| REQ-AGG-RES-013 | automated | AT-RES-13 · GuardedSourceTest#shouldNotRetryWhenTimeoutOrNotFound | ✅ |
| REQ-AGG-RES-014 | automated | AT-RES-14 · GuardedSourceTest#shouldOpenCircuitWhenHalfOfLastTwentyCallsFail; AT-RES-14b · ProductRestApiIT#shouldOpenPricingCircuitWhenPricingKeepsTimingOut; AT-RES-14c · GuardedSourceTest#shouldCountInterruptedRetryAsTimeoutWhenBudgetIsSpent | ✅ |
| REQ-AGG-RES-015 | automated | AT-RES-15 · ProductRestApiIT#shouldShortCircuitPricingWhenCircuitOpen | ✅ |
| REQ-AGG-RES-016 | automated | AT-RES-16 · GuardedSourceTest#shouldResumeAfterOpenPeriodWhenTrialCallsSucceed, #shouldReopenWhenTrialCallsFail | ✅ |
| REQ-AGG-RES-017 | automated | AT-RES-17 · GuardedSourceTest#shouldIgnoreNotFoundWhenCountingFailures | ✅ |
| REQ-AGG-RES-018 | automated | AT-RES-18 · AggregatorPropertiesTest#shouldRefuseToStartWhenBudgetsInvalid | ✅ |
| REQ-AGG-RES-019 | automated | AT-ROB-03 · CatalogSourceTest#shouldFailAsUnavailableWhenBodyIsEmpty, PricingSourceTest#shouldFailAsUnavailableWhenRequiredFieldMissing | ✅ |
| RC-AGG-RES-001 | automated | AT-RES-RC1 · ProductRestApiIT#shouldKeepDiscountWhenCustomerFails | ✅ |
| RC-AGG-RES-002 | automated | AT-ROB-06 · GuardedSourceTest#shouldNotCountCancellationWhenRequestAlreadyFailed; AT-ROB-07 · ProductAggregationServiceTest#shouldReportUnfinishedSourcesAsCancelledUnlessPastTheirDeadline; AT-RES-14b | ✅ |

## Where the code lives
`aggregator/src/main/java/com/kramp/aggregator/aggregation/` (`GuardedSource`, `ProductAggregationService`), `…/config/AggregatorProperties.java`, `…/source/UpstreamHttp.java`.

## Connected
UR-3, UR-4, UR-5, UR-7; ADR-0003 budgets and circuit breaking.
