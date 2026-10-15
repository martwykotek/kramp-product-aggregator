# Design spec — Product Information Aggregator

| | |
|---|---|
| Status | Approved (v2, 2026-10-14) — v2 adds eight clarifications found while specifying the tests |
| Author | Dawid Kłos |
| Requirements | [`specs/`](../../specs/index.md) — 77 requirements derived from this spec |
| Tests | automated tests named in each `specs/` leaf; validation on the running system: [validation report](../verification/validation-report.md) |

## 1. Goal and success criteria

A backend service that, for `productId + market (+ optional customerId)`, combines Catalog, Pricing,
Availability and Customer data into one market-aware response that survives partial upstream failures.

Success means:
1. `docker compose up --build` runs everything with Docker as the only prerequisite; `./mvnw verify` works with JDK 21.
2. Every constraint of the assignment holds and is proven by a test: Catalog fails → request fails; Pricing fails → product shown with price unavailable; Availability fails → stock unknown; Customer fails or no ID → standard response.
3. Mocks behave like real services over HTTP: long-tailed latency, failures at the stated reliability, several failure kinds, and a Chaos API to show failure handling live.
4. Real Kramp products and a Kramp-styled demo page make the scenarios tangible.
5. The README covers everything the assignment asks for, including Design Question A.
6. Every significant decision is recorded with its alternatives ([ADRs](../adr/)).

**Non-goals:** real authentication, persistence, caching, cloud deployment, horizontal scaling.

## 2. Constraints

- Prices, stock, customers and discounts are **fictitious** (Kramp prices are behind a login) and labelled so. Product numbers, brands, localized names, categories, images, shop links, warehouse locations and the delivery promise are **real** (kramp.com, October 2026).
- No Kramp logo file is copied; the demo page uses a text wordmark.
- The assignment suggests 3–4 hours: code stays simple enough to defend line by line (YAGNI).

## 3. Stack (versions verified on Maven Central, 2026-10-14)

| Concern | Choice |
|---|---|
| Language | Java 21 (virtual threads; no preview features; no `synchronized` around blocking I/O, because on Java 21 it pins the carrier thread) |
| Framework | Spring Boot 4.1.1 — `webmvc`, `restclient`, `actuator`, `grpc-server` starters |
| Resilience | resilience4j 2.4.0 (`circuitbreaker`, `micrometer`), used programmatically |
| HTTP client | Spring `RestClient` on the JDK `HttpClient` (HTTP/1.1, connect + read timeout) |
| gRPC | Spring Boot 4.1 gRPC starter (spring-grpc 1.1.1, grpc-java from the Boot BOM); code generated from `.proto` |
| API docs | springdoc-openapi 3.1.1 (Swagger UI) |
| JSON | Jackson 3 |
| Tests | JUnit 6, AssertJ, Mockito, WireMock 3.13.2, ArchUnit 1.5.1, in-process gRPC |
| Quality | Spotless 3.10.4 + google-java-format; JaCoCo 0.8.15 (report only) |
| Build / run | Maven multi-module with wrapper; multi-stage Docker images (JRE 21, non-root, HEALTHCHECK); `docker-compose.yml` |
| CI | GitHub Actions: `./mvnw -B verify` + image builds |

## 4. Architecture

```
kramp-product-aggregator/
├── aggregator/        service: REST :8080, gRPC :9090, demo page at "/"
├── upstream-mocks/    one app, four simulated upstreams under /catalog /pricing /availability /customers + /chaos (:8081)
├── quality-gates/     requirement traceability gate, README contract
├── specs/             requirements (REQ/RC) and how each is proven
├── .changes/          change record CR-001: user requirements, validation protocol, approvals
├── docs/              adr/, design/, verification/
├── scripts/demo.sh    scripted demo of the interview scenarios
└── docker-compose.yml
```

Packages in `aggregator` (hexagonal-lite; ArchUnit keeps `aggregation` free of transport and upstream code):

| Package | Responsibility |
|---|---|
| `api.rest` | `ProductController`, `ProblemDetail` error mapping |
| `api.grpc` | gRPC service, proto mapping, status mapping |
| `presentation` | `ProductViewService` (input validation → market → aggregation → view), `ProductViewAssembler`, localized labels (`MessageSource`), price formatting |
| `aggregation` | `ProductAggregationService` (orchestration), `ProductDataSource`, `GuardedSource` (breaker + budgeted retry), `SourceOutcome`, `AggregationResult` |
| `source.catalog` / `.pricing` / `.availability` / `.customer` | HTTP client + `ProductDataSource` implementation + upstream DTOs |
| `market` | `Market` (code, locale, currency), `MarketRegistry` from configuration |
| `observability` | correlation-id filter, MDC propagation onto virtual threads, summary log line, metrics, health |
| `config` | wiring: executor, circuit breakers, guarded sources, typed properties |

**Extensibility (Design Question A):** a new source is a new `source.x` package (client + `ProductDataSource` bean) plus one configuration block (URL, budget). Orchestration, timeouts, breakers, `meta.sources` and degradation apply unchanged; only the view gets one new section and its mapping. This is proven by a test that registers a test-only `related` source (REQ-AGG-VIEW-007).

## 5. API contract

### REST

`GET /api/v1/products/{productId}?market={marketCode}`

| Input | Where | Rules |
|---|---|---|
| `productId` | path | required, `[A-Za-z0-9-]{1,40}` (Kramp item numbers such as `W6103`, `62052RSC3SKF`) |
| `market` | query | required; one of the configured markets (§8); case and `_`/`-` tolerant |
| `X-Customer-Id` | header | optional, `[A-Za-z0-9-]{1,64}`; a header keeps it out of URLs, access logs and caches (in production it would come from the token) |
| `X-Correlation-Id` | header | optional; generated when absent; always echoed |

**200** whenever Catalog returned the product (values the tests assert):

```json
{
  "productId": "W6103",
  "market": "pl-PL",
  "language": "pl",
  "product": {
    "name": "Filtr oleju Mann & Hummel", "brand": "MANN-FILTER", "description": "…",
    "category": "Filtry wymienne", "ean": "4011558738402", "salesUnit": "EA",
    "specs": [{ "label": "Gwint montażowy", "value": "M20 x 1.50 mm (metric)" }],
    "images": ["https://images.kramp.com/images/60/08/w6103.jpg"],
    "shopUrl": "https://www.kramp.com/shop-pl/pl/p/filtr-oleju-mann-hummel--W6103"
  },
  "pricing": {
    "status": "AVAILABLE", "currency": "PLN", "priceType": "NET",
    "basePrice": 64.90, "discountPercent": 12, "finalPrice": 57.11,
    "formatted": { "basePrice": "64,90 zł", "finalPrice": "57,11 zł" },
    "label": null
  },
  "availability": {
    "status": "IN_STOCK", "stockLevel": 42,
    "warehouse": { "code": "PL-MOD", "city": "Modła Królewska", "country": "PL" },
    "expectedDelivery": "2026-10-15",
    "label": "Dostępny, dostawa 15 paź 2026"
  },
  "personalization": { "status": "PERSONALIZED", "segment": "WORKSHOP", "preferredBrand": true },
  "meta": {
    "degraded": false,
    "correlationId": "…",
    "sources": {
      "catalog": { "status": "OK", "durationMs": 48 },
      "pricing": { "status": "OK", "durationMs": 83 },
      "availability": { "status": "OK", "durationMs": 97 },
      "customer": { "status": "OK", "durationMs": 61 }
    }
  }
}
```

- `pricing.status`: `AVAILABLE` | `UNAVAILABLE` (price fields null, localized `label`).
- `availability.status`: `IN_STOCK` | `OUT_OF_STOCK` | `UNKNOWN`; the label carries the localized delivery date, never relative words like "tomorrow".
- `personalization.status`: `PERSONALIZED` | `STANDARD`. `preferredBrand` tells whether the product's brand is one of the customer's preferred brands — a fact neither upstream knows alone.
- `meta.sources.*.status`: `OK` | `SKIPPED` | `NOT_FOUND` | `TIMEOUT` | `ERROR` | `CIRCUIT_OPEN`.
- `meta.degraded` is true when a called OPTIONAL source ended in `TIMEOUT`, `ERROR` or `CIRCUIT_OPEN`. `SKIPPED` and `NOT_FOUND` are business outcomes and do not degrade.

**Errors** are RFC 9457 `application/problem+json` with `code` and `correlationId`; upstream status codes and bodies never leak:

| Situation | HTTP | `code` |
|---|---|---|
| unsupported market | 400 | `UNSUPPORTED_MARKET` (+ `supportedMarkets`) |
| malformed `productId`, missing `market`, malformed `X-Customer-Id` | 400 | `INVALID_REQUEST` — nothing is sent upstream |
| unknown product | 404 | `PRODUCT_NOT_FOUND` |
| product outside the market's assortment | 404 | `PRODUCT_NOT_IN_MARKET` |
| Catalog error or Catalog circuit open | 503 + `Retry-After` | `CATALOG_UNAVAILABLE` |
| Catalog timeout | 504 | `CATALOG_TIMEOUT` |

### gRPC (:9090)

`kramp.aggregator.v1.ProductAggregator/GetProduct(GetProductRequest{product_id, market, optional customer_id})` returns the same view (money as decimal strings). Correlation id travels in metadata `x-correlation-id`. Failures map to `INVALID_ARGUMENT` (400), `NOT_FOUND` (404), `UNAVAILABLE` (503), `DEADLINE_EXCEEDED` (504). Both transports call the same `ProductViewService`. Server reflection is on so `grpcurl` works without the proto file.

## 6. Aggregation and failure handling

**Model:** `ProductDataSource<T>` (`name`, `criticality` REQUIRED/OPTIONAL, `isApplicable(request)`, `fetch(request)`); `SourceOutcome<T>` is sealed: `Ok`, `NotFound(reason)`, `Skipped`, `Failed(TIMEOUT | ERROR | CIRCUIT_OPEN)`, each with its duration.

**Algorithm:**
1. Validate input and resolve the market (400 if invalid).
2. Start every applicable source on a virtual thread (MDC copied); a source that is not applicable (Customer without an ID) is `Skipped` and never called.
3. Wait for REQUIRED sources first. If Catalog ends in anything but `Ok`, cancel the other calls and fail: `NotFound` → 404, `TIMEOUT` → 504, `ERROR`/`CIRCUIT_OPEN` → 503.
4. Wait for each OPTIONAL source up to its budget + 25 ms grace (so the HTTP client's own timeout fires first and is recorded by the breaker), never beyond the 500 ms global deadline; anything unfinished becomes `TIMEOUT` and is cancelled.
5. Assemble the view (labels, formatting) and emit one summary log line and metrics.

**Budgets** (typed configuration validated at start-up — the service refuses to start with invalid values):

| Source | Typical latency (assignment) | Budget | Policy |
|---|---|---|---|
| Catalog | 50 ms | 250 ms | REQUIRED |
| Pricing | 80 ms | 300 ms | OPTIONAL |
| Availability | 100 ms | 350 ms | OPTIONAL |
| Customer | 60 ms | 200 ms | OPTIONAL |
| Global deadline | — | 500 ms | safety net |

A budget covers all attempts of a source. Each attempt is bounded by the HTTP read timeout (= budget) and by the orchestrator deadline.

**Retry:** at most one, only after a fast failure (a 5xx answer) and only if the remaining budget is at least the source's typical latency. Never after a timeout, a not-found answer or an unusable body. A connection refused or reset before any answer is not retried by the aggregator: the JDK `HttpClient` already resends an idempotent request once by itself, and a second layer would make it four upstream calls.

**Circuit breaker** (one per source): count-based window of 20 calls, minimum 10, 50% failure threshold, 10 s open, 3 half-open trial calls. Not-found answers are ignored. **Calls the aggregator cancels (because Catalog already failed) release their permission without counting as failures** — otherwise a Catalog outage would open the other breakers and hide prices after recovery. `Retry-After` on a Catalog 503 is the configured open-state wait (10 s, an upper bound) when the circuit is open, otherwise 1.

**Outcome → response:**

| Source | Ok | Not found | Failed | Skipped |
|---|---|---|---|---|
| Catalog | product section | 404 `PRODUCT_NOT_FOUND` / `PRODUCT_NOT_IN_MARKET` | 504 (timeout) / 503 (error, circuit open) | never |
| Pricing | `AVAILABLE` | `UNAVAILABLE` "price on request", not degraded | `UNAVAILABLE` "temporarily unavailable", degraded | never |
| Availability | `IN_STOCK` / `OUT_OF_STOCK` | `UNKNOWN`, not degraded | `UNKNOWN`, degraded | never |
| Customer | `PERSONALIZED` | `STANDARD`, not degraded | `STANDARD`, degraded | `STANDARD` (no header) |

Further rules:
- An upstream `200` without a valid body (empty, malformed, missing required fields) is that upstream's failure — never a 500.
- A price in a currency other than the market's is treated as a Pricing failure; a price is never shown in the wrong currency.
- **Pricing owns discounts:** it receives the customer ID directly. If Customer fails, personalization falls back to `STANDARD` but the discount stays; if Pricing fails, there is no price even when Customer works.

**"Pricing starts timing out":** requests 1–10 return in ~300 ms with the price marked unavailable and `degraded: true`; the breaker then opens and responses return at the speed of the healthy sources, still without a price, while Pricing is no longer hammered; every 10 s three trial calls probe it; when Pricing recovers, prices come back without a restart.

## 7. Upstream mocks (`upstream-mocks`, :8081)

- **Latency:** log-normal, median = the assignment's typical latency, σ = ln(3)/2.326 ≈ 0.472 so p99 ≈ 3 × median. Real latencies are right-skewed; a fixed delay would hide tail behaviour.
- **Failures:** with probability 1 − reliability (99.9 / 99.5 / 98 / 99 %): 50 % fast 503 (~5 ms, overload), 30 % 500 after normal latency (bug), 20 % hang 2–5 s then 503 (GC pause / partition). Seedable (`MOCKS_SEED`) for reproducible runs.
- **Different error shapes per upstream** (different teams); the aggregator normalizes them.
- **Chaos API:** `PUT /chaos/{catalog|pricing|availability|customer}` with `{"mode":"HEALTHY|SLOW|TIMEOUT|DOWN|FLAKY","slowFactor":5,"reliability":0.5}`; `GET /chaos`; `DELETE /chaos` resets. CORS allows the demo page.

| Mock | Endpoint | Returns |
|---|---|---|
| Catalog | `GET /catalog/products/{id}?market=&lang=` | content in `lang` or English fallback (`language` says which); 404 `PRODUCT_NOT_FOUND` / `NOT_IN_ASSORTMENT` |
| Pricing | `GET /pricing/prices/{id}?market=[&customerId=]` | `currency`, `basePrice`, `discountPercent`, `finalPrice`, `priceType: NET`; 404 when not priced in the market |
| Availability | `GET /availability/stock/{id}?market=` | `stockLevel`, `warehouse`, `expectedDelivery` |
| Customer | `GET /customers/{id}` | `segment` (DEALER / WORKSHOP / CONSTRUCTION), `preferredBrands`; 404 unknown |

- **Products:** 15 real Kramp items (MANN-FILTER, HiFi Filter, Donaldson, Faster, Gates, Walterscheid, Comer, SKF, Grammer, Facom and five Kramp own-brand items) with names in en/nl/de/fr/pl as shown on kramp.com. The Facom spanner is not sold in Poland and the Kramp seat top not in the Netherlands — as in Kramp's shops — giving real "outside assortment" and "language fallback" cases.
- **Warehouses (real locations):** Varsseveld (NL, central), Strullendorf (DE), Poitiers (FR), Biggleswade (GB), Modła Królewska (PL). If the local warehouse is empty but Varsseveld has stock, delivery is one working day later.
- **Delivery promise (Kramp's rule):** order before 18:00 market time on a working day → next working day; later or at the weekend → one working day more. Time zone from the market; the clock is injected.

## 8. Markets and language

| Market | Language | Currency | Time zone | Serving warehouse |
|---|---|---|---|---|
| nl-NL | nl | EUR | Europe/Amsterdam | Varsseveld |
| nl-BE | nl | EUR | Europe/Brussels | Varsseveld |
| fr-BE | fr | EUR | Europe/Brussels | Varsseveld |
| fr-FR | fr | EUR | Europe/Paris | Poitiers |
| de-DE | de | EUR | Europe/Berlin | Strullendorf |
| de-AT | de | EUR | Europe/Vienna | Strullendorf |
| pl-PL | pl | PLN | Europe/Warsaw | Modła Królewska |
| en-GB | en | GBP | Europe/London | Biggleswade |

Labels come from `messages_{en,nl,de,fr,pl}.properties`; prices are formatted with `NumberFormat` for the market locale and currency; dates in JSON are ISO-8601. Adding a market is a configuration entry (plus a translation file for a new language).

## 9. Observability and operations

- **Correlation ID** accepted or generated, put in MDC, forwarded to every upstream, echoed in the response.
- **One summary log line per request:** `product_aggregated productId=… market=… customer=present|absent outcome=OK|DEGRADED|FAILED durationMs=… catalog=OK/48 pricing=TIMEOUT/300 availability=OK/97 customer=SKIPPED/0` — never the customer ID. JSON logs in the `docker` profile.
- **Metrics** (`/actuator/prometheus`): timer `upstream.call{source,outcome}`, counter `aggregation.result{outcome}`, resilience4j breaker metrics, HTTP server timers.
- **Health:** liveness and readiness never depend on upstreams (a Pricing outage must not take the aggregator out of the load balancer); an informational `upstreams` component shows each breaker's state.
- **Operating it:** SLO 99.9 % of responses without 5xx over 30 days, p99 < 400 ms. Alerts: `DEGRADED` share > 5 % for 5 min; any breaker open > 2 min; p99 > 400 ms for 10 min; Catalog-caused 5xx > 0.5 % for 5 min. Dashboard per upstream; runbook "Pricing timing out".

## 10. Demo page

One HTML file served at `/` in Kramp colours (`#9B1915`, `#FF9E00`) and fonts (Barlow, Barlow Condensed) with a text wordmark: product, market and customer pickers; a product card; a bar showing each upstream's status and time; a chaos panel that switches the mocks and fires 20 requests to show the breaker opening. The footer states that prices, stock and customers are fictitious.

## 11. Verification

Every requirement in `specs/` names its proof; every automated case had its exact expected output written down before the code, and a 20-step protocol validates the running system ([validation report](../verification/validation-report.md)). A build gate fails when a claimed proof does not resolve.

## 12. Design Question A — Related Products (200 ms, 90 %)

Related products are **optional**: a product can be shown and bought without them. In this design the source is one new package and one configuration block. The real problem is the budget: 200 ms typical with 10 % failures would stretch the slowest path of the main response (today ≈ 350 ms). Options: (a) a longer budget for this source only — the whole page waits; (b) **do not block the main response**: a separate `/products/{id}/related` endpoint the frontend loads lazily below the fold, with its own cache (relations change rarely: long TTL, stale-while-revalidate) that also masks most of the 10 % failures; (c) precompute relations asynchronously. Recommendation: (b), with the same budgeted retry and circuit breaker as other sources.

## 13. Out of scope (what I would do with more time)

Catalog cache with stale-if-error (conflicts with "Catalog fails → request fails" and needs a business decision), bulkheads per upstream, hedged requests, `StructuredTaskScope` (preview in Java 21), moving to Java 25 LTS (virtual threads without pinning, JEP 491), real authentication, rate limiting, distributed tracing (OpenTelemetry), contract tests with upstream teams, load tests.
