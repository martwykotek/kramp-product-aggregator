# Product Information Aggregator

A backend service for the Kramp assignment. For one product, one market and an optional customer, it
calls Catalog, Pricing, Availability and Customer in parallel and returns one market-aware view. Every
call is bounded in time. When an optional source fails, its section is marked as unavailable instead of
failing the page. The request fails only when Catalog cannot answer.

- REST `GET /api/v1/products/{productId}?market=pl-PL` with `X-Customer-Id`, and gRPC `GetProduct` that
  serves the same view.
- Four simulated upstreams as a separate HTTP service, with realistic latency and failures and a Chaos API.
- Real Kramp products, warehouses and delivery promise; fictitious prices, stock and customers.
- A demo page in Kramp's colours that shows each upstream call and lets you break the upstreams live.

Java 21, Spring Boot 4.1.1, resilience4j 2.4.0, virtual threads.

## Quick start

With Docker as the only prerequisite:

```bash
docker compose up --build
```

- Demo page: <http://localhost:8080/>
- REST: `curl -H 'X-Customer-Id: C-1002' 'http://localhost:8080/api/v1/products/W6103?market=pl-PL'`
- OpenAPI: <http://localhost:8080/swagger-ui.html>
- gRPC on `localhost:9090` (server reflection on); upstream mocks and the Chaos API on `localhost:8081`

Build and run every test, the format check and the requirements traceability gate with JDK 21 only:

```bash
./mvnw verify
```

`MOCKS_SEED=42 docker compose up` makes the mocks' random latency and failures repeatable; without it the
mocks log the seed they picked.

## Demo

**The page** at <http://localhost:8080/> has four parts:

- a product, market and customer picker with the product card (price, stock and delivery, segment and
  "preferred brand" badges). When the product cannot be shown, the card says why in the market's language,
  the way a customer would see it; after a 503 it retries by itself once `Retry-After` has passed. The
  problem details (code, `Retry-After`, correlation ids) sit under "Technical details";
- **How this answer was assembled**: one bar per upstream, all starting at zero because the calls run in
  parallel, with each call's status and time and each circuit's state from `/actuator/health`;
- **Break the upstreams**: switch any mock to Healthy, Slow, Timeout, Down or Flaky, then "Fire 20
  requests" to watch a circuit open;
- **Validation scenarios**: TC-01…TC-12 from the validation protocol, one click each. Every run starts from
  the same state (mocks deterministic, all circuits closed, 20 warm-up requests), shows each response next
  to the expected outcome and resets the mocks afterwards. A person judges the result; the page does not.

Mocks and circuit breakers are shared by everyone using the stack, so run scenarios from one page or
terminal at a time: requests from a second tab land in the same breaker windows.

**In a terminal**, `scripts/demo.sh` (curl and jq) walks through the customer in Poland, the request
without a customer, the language fallback, a product outside the market and the Pricing timeout scenario.
Every validation step is also in `docs/verification/aggregator.http` for the IntelliJ HTTP Client.

```bash
# Pricing starts timing out
curl -X PUT localhost:8081/chaos/pricing -H 'Content-Type: application/json' -d '{"mode":"TIMEOUT"}'
for i in $(seq 20); do
  curl -s -H 'X-Customer-Id: C-1002' 'localhost:8080/api/v1/products/W6103?market=pl-PL' \
    | jq -c '.meta.sources.pricing'
done
curl -X DELETE localhost:8081/chaos          # back to healthy

# The same view over gRPC
grpcurl -plaintext -d '{"product_id":"W6103","market":"pl-PL","customer_id":"C-1002"}' \
  localhost:9090 kramp.aggregator.v1.ProductAggregator/GetProduct
```

## Architecture

```
REST :8080 ──┐                                                       ┌──► Catalog        required
             ├──► ProductViewService ──► ProductAggregationService ──┼──► Pricing        optional
gRPC :9090 ──┘    validates input,       one virtual thread per      ├──► Availability   optional
                  resolves the market,   source, deadlines, fails    └──► Customer       optional, only with a customer id
                  assembles the view     fast when Catalog fails
                                         each call through a GuardedSource         (HTTP to upstream-mocks :8081)
                                         = circuit breaker + one budgeted retry
```

| Module | What it is |
|---|---|
| `aggregator` | the service: REST, gRPC, demo page, actuator |
| `upstream-mocks` | one Spring Boot app serving the four upstreams over HTTP, plus `/chaos` |
| `quality-gates` | the requirements traceability gate and the README contract test |

Packages in `aggregator`, from the outside in: `api.rest` and `api.grpc` (transport and error mapping) →
`presentation` (input validation, labels, price formatting, the view) → `aggregation` (orchestration,
`GuardedSource`, outcomes) ← `source.catalog|pricing|availability|customer` (HTTP clients). `market`,
`observability` and `config` support them. `ArchitectureTest` (ArchUnit) keeps the dependencies pointing
inwards: `aggregation` never depends on transport, presentation or upstream adapters, and neither the
sources nor `presentation` depend on transport.

Every source implements one interface:

```java
public interface ProductDataSource<T> {
  String name();
  Criticality criticality();                       // REQUIRED or OPTIONAL
  default boolean isApplicable(ProductRequest request) { return true; }
  T fetch(ProductRequest request);                 // throws not-found, unavailable or timeout
}
```

A new source is a new bean plus one configuration block (base URL, budget, typical latency). The
orchestrator, budgets, circuit breaker, `meta.sources` and the degraded flag apply to it unchanged;
`ExtensibilityIT` proves this by registering a test-only `related` source.

## Walkthrough: a customer in Poland

`GET /api/v1/products/W6103?market=pl-PL` with `X-Customer-Id: C-1002`:

1. **Correlation.** The filter accepts the caller's `X-Correlation-Id` or generates one, puts it in the
   logging context and echoes it in the response. Every upstream call carries it.
2. **Validation.** The product id, the market and the customer id are checked before any upstream call.
   `pl-PL` (also `PL_pl`) resolves to Polish and PLN; an unknown market is a 400 listing the 8 markets.
3. **Fan-out.** Four calls start at once on virtual threads: Catalog with `lang=pl`, Pricing with the
   customer id (Pricing owns discount agreements), Availability for the Polish market, Customer for the
   profile. Each has its own budget (Catalog 250, Pricing 300, Availability 350, Customer 200 ms) and
   passes through its circuit breaker.
4. **Catalog first.** Catalog is the only required source; had it failed, the other calls would be
   cancelled and the answer would be 503, 504 or 404.
5. **Assembly.** Polish labels, the price formatted for `pl-PL`, the delivery date from the Polish
   warehouse, and `preferredBrand`, a fact neither upstream knows alone: MANN-FILTER is one of the
   customer's preferred brands.
6. **One log line** per aggregation, without the customer id:
   `product_aggregated productId=W6103 market=pl-PL customer=present outcome=OK durationMs=81 catalog=OK/49 pricing=OK/74 availability=OK/44 customer=OK/80`

```json
{
  "productId": "W6103", "market": "pl-PL", "language": "pl",
  "product": { "name": "Filtr oleju Mann & Hummel", "brand": "MANN-FILTER", "category": "Filtry wymienne", "…": "…" },
  "pricing": { "status": "AVAILABLE", "currency": "PLN", "priceType": "NET",
               "basePrice": 64.90, "discountPercent": 12, "finalPrice": 57.11,
               "formatted": { "basePrice": "64,90 zł", "finalPrice": "57,11 zł" }, "label": null },
  "availability": { "status": "IN_STOCK", "stockLevel": 42,
                    "warehouse": { "code": "PL-MOD", "city": "Modła Królewska", "country": "PL" },
                    "expectedDelivery": "2026-10-16", "label": "Dostępny, dostawa 16 paź 2026" },
  "personalization": { "status": "PERSONALIZED", "segment": "WORKSHOP", "preferredBrand": true },
  "meta": { "degraded": false, "correlationId": "…",
            "sources": { "catalog": { "status": "OK", "durationMs": 49 }, "pricing": { "status": "OK", "durationMs": 74 },
                         "availability": { "status": "OK", "durationMs": 44 }, "customer": { "status": "OK", "durationMs": 80 } } }
}
```

The delivery date follows Kramp's promise: ordered before 18:00 Warsaw time on a working day, delivered
the next working day; later, or at the weekend, one working day more. The time zone comes from the market.
The same product in `de-AT` comes in German with euro prices from Strullendorf, and `TS19100KR` in `nl-BE`
falls back to English content (`"language": "en"`) while the labels stay Dutch.

## Failure handling

| Source fails with… | Response |
|---|---|
| Catalog: timeout | 504 `CATALOG_TIMEOUT` at ≈ 250 ms; other calls cancelled (`CANCELLED` in the log line) |
| Catalog: error or open circuit | 503 `CATALOG_UNAVAILABLE`, `Retry-After: 1` (10 when the circuit is open) |
| Catalog: unknown product / not sold in the market | 404 `PRODUCT_NOT_FOUND` / `PRODUCT_NOT_IN_MARKET` |
| Pricing | 200, `pricing.status: UNAVAILABLE` with a localized label, `meta.degraded: true` |
| Availability | 200, `availability.status: UNKNOWN`, degraded |
| Customer, or no customer id | 200, `personalization.status: STANDARD`; the discount from Pricing stays |

- Errors are `application/problem+json` with a stable `code` and the `correlationId`; upstream status codes
  and bodies never reach the client.
- A not-found answer from an optional source is a business answer (no price in this market, unknown
  customer): the section says so, and the response is not degraded.
- An upstream `200` with an empty or broken body counts as that upstream's failure, and a price in a
  currency other than the market's is never shown.
- All optional sources hanging still answers in ≈ 350 ms (the longest optional budget), within the 500 ms
  global deadline.

## When Pricing starts timing out

What a client sees, measured on this stack (Pricing switched to TIMEOUT, then 20 requests):

1. **Requests 1–10** take ≈ 310 ms: Pricing's 300 ms budget runs out on each one. The product, stock and
   personalization are shown; `pricing.status` is `UNAVAILABLE` with "Cena chwilowo niedostępna" and
   `meta.degraded` is `true`. A timeout is never retried: the budget is already spent.
2. **The circuit opens** once half of the last 20 Pricing calls have failed (10 timeouts after a healthy
   period).
3. **Requests 11–20** no longer call Pricing at all (`CIRCUIT_OPEN`, 0 ms). The response takes as long as
   the slowest healthy source, typically 50–250 ms here, and the price stays hidden. Pricing gets time to
   recover instead of more load.
4. **After 10 s** the circuit lets three trial calls through. If they succeed it closes and prices are
   back without a restart; if two of the three fail it opens for another 10 s.

Throughout, `/actuator/health` stays `UP` and readiness never depends on an upstream, so a Pricing outage
cannot take the aggregator out of a load balancer; `components.upstreams.details.pricing` shows `OPEN`.

One subtle case is handled on purpose. When Catalog fails, the aggregator cancels the in-flight optional
calls. Counted as failures, those cancellations would open the Pricing, Availability and Customer circuits
during a Catalog outage and keep prices hidden after Catalog recovers. Cancelled calls therefore release
their circuit-breaker permission without counting, while real timeouts still count (the aggregator waits
budget + 25 ms so the HTTP client's own timeout is recorded first). This gap was found by writing the test
specification before any code (P8 in the decision log). The review then found the mirror case: a retry
starts with a full read timeout, so the aggregator may interrupt it first. An attempt interrupted after its
source's budget is spent therefore counts as that source's timeout; one interrupted earlier is `CANCELLED`
and appears neither in the circuit breaker nor in `upstream_call_seconds`.

## Key decisions and trade-offs

| Decision | Why | Trade-off accepted |
|---|---|---|
| Virtual threads and a blocking `RestClient` instead of WebFlux ([ADR-0001](docs/adr/0001-virtual-threads-over-webflux.md)) | readable imperative fan-out with the scalability this I/O needs | on Java 21, no `synchronized` around blocking calls (none in the code base) |
| Mocks as a separate HTTP service ([ADR-0002](docs/adr/0002-mocks-as-separate-http-process.md)) | real sockets, timeouts and serialization; live chaos | a second container |
| Budgets, one budgeted retry, a circuit breaker per source ([ADR-0003](docs/adr/0003-budgets-and-circuit-breaking.md)) | bounded time for slow connections; no retry storms | ≈ 10 slow responses before a circuit opens |
| 200 with a status per section ([ADR-0004](docs/adr/0004-200-with-section-status.md)) | one contract for full and partial pages | monitoring must watch `meta.degraded`, not only status codes |
| Customer id in a header ([ADR-0005](docs/adr/0005-customer-id-in-header.md)) | keeps personal data out of URLs, logs and caches | less convenient to try in a browser |
| Pricing owns discounts | it already receives the customer id | if Customer fails the discount stays; if Pricing fails there is no price |
| Java 21, not Java 25 | the version I can defend line by line | virtual-thread pinning in `synchronized` (avoided by rule) |
| Spring Boot 4.1.1 | latest GA, gRPC starters inside Boot | newer APIs (Jackson 3), checked in a spike first |
| resilience4j called from code, not annotations | the budget-aware retry and the cancellation rule are explicit and testable with an injected clock | a few more lines than `@CircuitBreaker` |

Every decision, its alternatives and who took it: [docs/design/decision-log.md](docs/design/decision-log.md).

## How I would operate it

- **SLO**: 99.9 % of responses without a 5xx over 30 days; p99 latency under 400 ms.
- **Alerts**: share of degraded responses above 5 % for 5 minutes; any circuit open for more than 2
  minutes; p99 above 400 ms for 10 minutes; Catalog-caused 5xx above 0.5 % for 5 minutes.
- **Metrics** on `/actuator/prometheus`: `upstream_call_seconds_*{source,outcome}` per upstream call,
  `aggregation_result_total{outcome}` (OK / DEGRADED / FAILED), `resilience4j_circuitbreaker_state`, and
  the HTTP server timers. One dashboard row per upstream: latency, outcomes, circuit state.
- **Health**: `/actuator/health/liveness` and `/actuator/health/readiness` never depend on upstreams; the
  `upstreams` component of `/actuator/health` shows each circuit (`CLOSED` / `OPEN` / `HALF_OPEN`).
- **Logs**: one `product_aggregated` line per aggregation with the outcome and each source's status and time
  (a request rejected before aggregation, such as an unsupported market, has none),
  JSON in the `docker` profile, always with the correlation id and never with the customer id.
- **Runbook "Pricing timing out"**: confirm with `upstream_call_seconds_count{source="pricing",outcome="TIMEOUT"}`
  and the circuit state (calls cancelled during a Catalog outage are not in this metric); customers see products without prices, nothing to restart; contact the Pricing
  team; prices return by themselves within 10 s of Pricing recovering.
- **Configuration**: budgets, circuit settings, markets and upstream URLs live in `application.yml` and are
  validated at start-up; an invalid value stops the service instead of running with a default.

## What I would do with more time

- A Catalog cache with stale-if-error. It conflicts with "Catalog fails → the request fails", so it needs a
  business decision first.
- Bulkheads per upstream, and hedged requests for the long latency tail.
- `StructuredTaskScope` for the fan-out once it is final, and Java 25 LTS (virtual threads no longer pin in
  `synchronized`, JEP 491).
- Real authentication (customer id from the token), rate limiting.
- Distributed tracing with OpenTelemetry instead of a correlation id only.
- Contract tests with the upstream teams, and load tests (Gatling or k6) against the latency budget.
- Findings from the final review I accepted for now (`.changes/CR-001_review.md`): a 404 counts as "not
  found" only when the body has the upstream's own error shape, so a missing route is not mistaken for an
  empty catalogue (F10); a contract violation in one record should not count against the whole upstream's
  circuit (F11); a cap on buffered upstream bodies, and a `Throwable` inside a source releasing its breaker
  permission (F6).

## Design question A — Related Products

> A new Related Products service: 200 ms typical latency, 90 % reliability. How would it fit?

**Optional, not required**: a product can be shown and bought without related products, so their failure
must never fail the page.

**Fitting it in is cheap.** It is one `source.related` package (HTTP client + `ProductDataSource` bean),
one configuration block and one new section in the view. Orchestration, the circuit breaker,
`meta.sources` and the degraded flag apply unchanged; `ExtensibilityIT` already proves this with a
test-only `related` source.

**The real problem is the budget.** Today the slowest path of the main response is ≈ 350 ms
(Availability). A source at 200 ms typical, with a long tail and 10 % failures, would either need a budget
that stretches every product page, or a short one that drops related products on a large share of
requests.

Options:

1. Put it in the main fan-out with its own longer budget. Simple, but the whole page waits for the
   least important section.
2. **Recommended: do not block the main response.** Serve related products from a separate endpoint,
   `/api/v1/products/{id}/related`, which the frontend loads lazily below the fold. Give it its own cache:
   relations change rarely, so a long TTL with stale-while-revalidate hides most of the 10 % failures and
   the latency. It still uses a `GuardedSource` with a budgeted retry and its own circuit breaker.
3. Precompute relations asynchronously (events or a nightly job) into a store the aggregator reads quickly.
   The best latency, but a new pipeline to own.

I would start with option 2 and move to option 3 only if the cache hit rate stays low.

## Data: what is real and what is fictitious

| Real (from kramp.com, October 2026) | Fictitious, and labelled so |
|---|---|
| 15 product numbers, brands, localized names (en / nl / de / fr / pl), categories, specifications, image and shop links | prices, discounts and price lists (Kramp prices are behind a login) |
| which shops sell which product (`10112PB` not in Poland, `TS19100KR` not in the Netherlands) | stock levels |
| warehouse locations: Varsseveld, Strullendorf, Poitiers, Biggleswade, Modła Królewska | customers `C-1001`…`C-1006`, their segments and preferred brands |
| the delivery promise: order before 18:00, delivered next working day | that `TS19100KR` is sold in `nl-BE` without a Dutch text (a fixture assumption for the language fallback) |

No Kramp logo file is included; the demo page uses a text wordmark. The page's footer states that prices,
stock and customers are fictitious. Images load from Kramp's public image server, with a placeholder if
one fails.

## Requirements, tests and traceability

The assignment was broken down into 11 user requirements and **79 system requirements** (68 proven by
automated tests, 11 by observed validation steps), each in a leaf under [`specs/`](specs/index.md).
Every test case was specified with its exact expected result before the code:
[docs/verification/test-specification.md](docs/verification/test-specification.md).

A test proves a requirement with a `// proves: REQ-…` comment above it. The requirements traceability gate
runs in `./mvnw verify` and fails the build when a claimed proof does not resolve;
`./mvnw verify -Dgate.strict=true` also fails while any requirement is unproven. The manual validation
protocol (TC-01…TC-19) and its signed results are in
[`.changes/CR-001_product-aggregator.md`](.changes/CR-001_product-aggregator.md).

## Use of AI tools

I used Claude Code as a coding assistant, as the assignment allows.
Every design decision and trade-off went through me; I reviewed and validated the result
and can walk through any part of the code.
