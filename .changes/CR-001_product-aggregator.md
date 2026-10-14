# CR-001 — Product Information Aggregator

Status: in-progress · Profile: lean · Acceptance: none · Repos: kramp-product-aggregator
Touches: REQ-AGG-VIEW-*, REQ-AGG-MARKET-*, REQ-AGG-RES-*, RC-AGG-RES-*, REQ-API-REST-*, RC-API-REST-*, REQ-API-GRPC-*, REQ-OPS-OBS-*, RC-OPS-OBS-*, REQ-MOCK-UPSTREAM-*, REQ-MOCK-CHAOS-*, REQ-DEMO-PAGE-*, RC-DEMO-PAGE-*, REQ-DLV-SUBMIT-*

## Summary
Purpose: client applications (web, mobile, dealer tools) make several calls per product page; slow or failing services slow the page down or break it, and the merging logic is duplicated in every client.
Solution: one market-aware endpoint (REST + gRPC) that fans out to Catalog, Pricing, Availability and Customer in parallel, bounds every call in time, degrades optional data with explicit section statuses and fails the request only when Catalog fails.

## User requirements
| UR | Requirement | Assignment section |
|---|---|---|
| UR-1 | As a client application (web, mobile, dealer tools), I want one call that returns product, price, stock and customer context, so that pages load fast and logic is not duplicated. | Context; Your Task; Functional 1–2 |
| UR-2 | As a customer in a given market, I want content, prices and delivery information appropriate to my market and language. | Functional 4 |
| UR-3 | As a customer, I want to see the product even when price, stock or personalisation is unavailable, clearly marked as such. | Functional 3; Non-functional 2; Constraints 2–4 |
| UR-4 | As a customer, I want a clear error instead of a broken page when basic product information cannot be shown. | Constraint 1 |
| UR-5 | As a customer on a slow rural connection, I want the page to respond in bounded time even when internal services are slow. | Context; Non-functional 1 |
| UR-6 | As the platform team, I want to add new data sources without major refactoring. | Non-functional 3; Design Question A |
| UR-7 | As an operator, I want to see, diagnose and contain upstream failures. | What we're looking for 3 |
| UR-8 | As the reviewer, I want upstream mocks that behave realistically: latency and failures per the stated reliability. | Data Sources; Important; The mocks matter |
| UR-9 | As an API consumer, I want a REST endpoint and, as a bonus, a gRPC endpoint. | Constraint 5 |
| UR-10 | As the reviewer, I want to run the service locally with minimal setup and read a README covering run, decisions, trade-offs, future work and the design question. | Deliverables 5–6; Submission |
| UR-11 | As the interviewer, I want to walk through "customer in Poland" and "Pricing starts timing out" end to end. | Scenarios to Consider |

## Inputs
Each system requirement, the user requirement it serves and its acceptance criterion. Requirement text and proof status live in the `specs/` leaves; test cases (AT-…, TC-…) are specified in `docs/verification/test-specification.md`.

| ID | Serves UR | Acceptance criterion |
|---|---|---|
| REQ-AGG-VIEW-001 | UR-1 | AT-VIEW-01 (test specification §4) |
| REQ-AGG-VIEW-002 | UR-1 | AT-VIEW-02 (test specification §4) |
| REQ-AGG-VIEW-003 | UR-1 | AT-VIEW-03 (test specification §4) |
| REQ-AGG-VIEW-004 | UR-5 | AT-VIEW-04 (test specification §4) |
| REQ-AGG-VIEW-005 | UR-3 | AT-VIEW-05 (test specification §4) |
| REQ-AGG-VIEW-006 | UR-1 | AT-VIEW-06 (test specification §4) |
| REQ-AGG-VIEW-007 | UR-6 | AT-VIEW-07 (test specification §4) |
| REQ-AGG-MARKET-001 | UR-2 | AT-MARKET-01 (test specification §4) |
| REQ-AGG-MARKET-002 | UR-2 | AT-MARKET-02 (test specification §4) |
| REQ-AGG-MARKET-003 | UR-2 | AT-MARKET-03 (test specification §4) |
| REQ-AGG-MARKET-004 | UR-2 | AT-MARKET-04 (test specification §4) |
| REQ-AGG-MARKET-005 | UR-2 | AT-MARKET-05 (test specification §4) |
| REQ-AGG-MARKET-006 | UR-2 | AT-MARKET-06 (test specification §4) |
| REQ-AGG-MARKET-007 | UR-2 | AT-MARKET-07 (test specification §4) |
| REQ-AGG-MARKET-008 | UR-2 | AT-ROB-04 (test specification §4) |
| REQ-AGG-RES-001 | UR-4 | AT-RES-01 (test specification §4) |
| REQ-AGG-RES-002 | UR-4 | AT-RES-02 (test specification §4) |
| REQ-AGG-RES-003 | UR-4 | AT-RES-03 (test specification §4) |
| REQ-AGG-RES-004 | UR-5 | AT-RES-04 (test specification §4) |
| REQ-AGG-RES-005 | UR-3 | AT-RES-05 (test specification §4) |
| REQ-AGG-RES-006 | UR-3 | AT-RES-06 (test specification §4) |
| REQ-AGG-RES-007 | UR-3 | AT-RES-07 (test specification §4) |
| REQ-AGG-RES-008 | UR-3, UR-7 | AT-RES-08 (test specification §4) |
| REQ-AGG-RES-009 | UR-3 | AT-RES-09 (test specification §4) |
| REQ-AGG-RES-010 | UR-5 | AT-RES-10 (test specification §4) |
| REQ-AGG-RES-011 | UR-5 | AT-RES-11 (test specification §4) |
| REQ-AGG-RES-012 | UR-3 | AT-RES-12 (test specification §4) |
| REQ-AGG-RES-013 | UR-7 | AT-RES-13 (test specification §4) |
| REQ-AGG-RES-014 | UR-7 | AT-RES-14; AT-RES-14b (test specification §4) |
| REQ-AGG-RES-015 | UR-5 | AT-RES-15 (test specification §4) |
| REQ-AGG-RES-016 | UR-7 | AT-RES-16 (test specification §4) |
| REQ-AGG-RES-017 | UR-7 | AT-RES-17 (test specification §4) |
| REQ-AGG-RES-018 | UR-7 | AT-RES-18 (test specification §4) |
| REQ-AGG-RES-019 | UR-3, UR-4 | AT-ROB-03 (test specification §4) |
| RC-AGG-RES-001 | UR-3 | AT-RES-RC1 (test specification §4) |
| RC-AGG-RES-002 | UR-7 | AT-ROB-06; AT-RES-14b (test specification §4) |
| REQ-API-REST-001 | UR-9 | AT-VIEW-01 (test specification §4) |
| REQ-API-REST-002 | UR-1 | AT-REST-02 (test specification §4) |
| REQ-API-REST-003 | UR-4 | AT-REST-03 (test specification §4) |
| REQ-API-REST-004 | UR-4 | AT-REST-04 (test specification §4) |
| REQ-API-REST-005 | UR-4 | AT-REST-05 (test specification §4) |
| REQ-API-REST-006 | UR-10 | AT-REST-06 (test specification §4) |
| REQ-API-REST-007 | UR-4 | AT-ROB-02 (test specification §4) |
| RC-API-REST-001 | UR-7 | AT-REST-RC1 (test specification §4) |
| REQ-API-GRPC-001 | UR-9 | AT-GRPC-01 (test specification §4) |
| REQ-API-GRPC-002 | UR-9 | AT-GRPC-02 (test specification §4) |
| REQ-API-GRPC-003 | UR-10 | TC-14 (grpcurl list) (CR-001 § Validation) |
| REQ-OPS-OBS-001 | UR-7 | AT-OBS-01 (test specification §4) |
| REQ-OPS-OBS-002 | UR-7 | AT-OBS-02 (test specification §4) |
| REQ-OPS-OBS-003 | UR-7 | AT-OBS-03 (test specification §4) |
| REQ-OPS-OBS-004 | UR-7 | AT-OBS-04 (test specification §4) |
| REQ-OPS-OBS-005 | UR-7 | AT-OBS-05 (test specification §4) |
| REQ-OPS-OBS-006 | UR-7 | AT-OBS-06 (test specification §4) |
| RC-OPS-OBS-001 | UR-7 | AT-OBS-RC1 (test specification §4) |
| REQ-MOCK-UPSTREAM-001 | UR-8 | AT-MOCK-01 (test specification §4) |
| REQ-MOCK-UPSTREAM-002 | UR-8 | AT-MOCK-02 (test specification §4) |
| REQ-MOCK-UPSTREAM-003 | UR-8 | AT-MOCK-03 (test specification §4) |
| REQ-MOCK-UPSTREAM-004 | UR-8 | AT-MOCK-04 (test specification §4) |
| REQ-MOCK-UPSTREAM-005 | UR-8 | AT-MOCK-05 (test specification §4) |
| REQ-MOCK-UPSTREAM-006 | UR-2 | AT-MOCK-06 (test specification §4) |
| REQ-MOCK-UPSTREAM-007 | UR-2 | AT-MOCK-07 (test specification §4) |
| REQ-MOCK-UPSTREAM-008 | UR-2 | AT-MOCK-08 (test specification §4) |
| REQ-MOCK-CHAOS-001 | UR-11 | AT-CHAOS-01 (test specification §4) |
| REQ-MOCK-CHAOS-002 | UR-11 | AT-CHAOS-02 (test specification §4) |
| REQ-MOCK-CHAOS-003 | UR-11 | AT-CHAOS-01 (test specification §4) |
| REQ-DEMO-PAGE-001 | UR-11 | TC-15 (CR-001 § Validation) |
| REQ-DEMO-PAGE-002 | UR-11 | TC-15 (CR-001 § Validation) |
| REQ-DEMO-PAGE-003 | UR-11 | TC-15 (CR-001 § Validation) |
| RC-DEMO-PAGE-001 | UR-10 | TC-15 (CR-001 § Validation) |
| RC-DEMO-PAGE-002 | UR-10 | TC-18 (CR-001 § Validation) |
| REQ-DLV-SUBMIT-001 | UR-10 | TC-17 (local part) (CR-001 § Validation) |
| REQ-DLV-SUBMIT-002 | UR-10 | TC-17 (local part) (CR-001 § Validation) |
| REQ-DLV-SUBMIT-003 | UR-10 | AT-DLV-01 (test specification §4) |
| REQ-DLV-SUBMIT-004 | UR-11 | AT-DLV-01 (test specification §4) |
| REQ-DLV-SUBMIT-005 | UR-10 | AT-DLV-01 (test specification §4) |
| REQ-DLV-SUBMIT-006 | UR-10 | AT-DLV-01 (test specification §4) |
| REQ-DLV-SUBMIT-007 | UR-10 | TC-17 (GitHub part) (CR-001 § Validation) |

## Risk assessment
| Risk | Mitigation |
|---|---|
| Scope beyond the 3–4 h guideline makes the solution harder to defend | YAGNI; ADRs and the README explain every decision; owner review of every class before submission |
| Time-dependent tests flaky on CI | injected clocks, fake sources and a fake nano clock for budgets; WireMock with fixed delays; explicit tolerances (150 ms) |
| Boot 4 / gRPC starter are new APIs | versions verified on Maven Central and in a spike before planning |
| Leaking identifiers from previous work into the public repository | no code copied 1:1; hygiene scan (kept outside the repository) before publishing |
| Kramp image URLs stop working | the demo page shows a placeholder when an image fails to load |
| Locale data differs between JDKs | one JDK 21 in CI and Docker; whitespace-normalised comparisons |
| Real mock randomness during manual validation | deterministic mode (FLAKY with reliability 1.0) and a documented single retry |
| Breaker state leaking between tests or validation steps | all breakers reset before each L3 test; reset procedure R between TCs |

## Decisions
- [DECISION: virtual threads + blocking RestClient | readable imperative code with the same scalability for an I/O fan-out | WebFlux/Reactor | high | medium]
- [DECISION: mocks as a separate HTTP process | real timeouts, connection pools and serialization | in-process fakes | high | low]
- [DECISION: resilience4j used programmatically with per-source time budgets | explicit, testable with an injected clock, no annotation magic | annotations / Spring Cloud Circuit Breaker / hand-rolled breaker | high | low]
- [DECISION: HTTP 200 with a status per section | clients render partial pages from one contract | 206 / error on any failure | medium | medium]
- [DECISION: customer id in the X-Customer-Id header | keeps it out of URLs, access logs and shared caches | query parameter | medium | low]

## Verification
Filled when implementation is complete: `| ID | Proof (by marker) | Evidence (CI run) | Result |`.

## Validation
Run against `docker compose up --build`; requests in `docs/verification/aggregator.http` or `scripts/demo.sh`.

### Environment and preconditions

1. Docker Desktop running; repo root; `docker compose up --build -d`; wait until `docker compose ps` shows both services `healthy`.
2. Tools: `curl`, `jq`, `grpcurl` (`brew install grpcurl`), a browser.
3. **Deterministic mode (D)** — for TCs that assert exact "all OK": switch every upstream to FLAKY with reliability 1.0 (latency model stays realistic, random failures off):
   ```bash
   for s in catalog pricing availability customer; do curl -s -X PUT localhost:8081/chaos/$s -H 'Content-Type: application/json' -d '{"mode":"FLAKY","reliability":1.0}'; done
   ```
   Latency tail beyond a budget is still possible (~0.1–0.3% for availability). If a single source shows `TIMEOUT` in a D-mode TC, repeat the request once and note it in Results.
4. **Reset (R)** between TCs: `curl -s -X DELETE localhost:8081/chaos`; if the previous TC opened a circuit, `docker compose restart aggregator` and wait for `healthy` (breakers live in the aggregator).
5. Note the local time: delivery oracles differ before/after 18:00 market time.

Base command used below: `P='localhost:8080/api/v1/products'`.

### Test cases

| Test Scope | Steps | Expected Outcome | Passed |
|---|---|---|---|
| TC-01: **Customer in Poland** (interview scenario) — REQ-AGG-VIEW-001/002, MARKET-005/006, MOCK-006 | D; `curl -s -H 'X-Customer-Id: C-1002' "$P/W6103?market=pl-PL" \| jq` | 200; `language` "pl"; `product.name` "Filtr oleju Mann & Hummel"; `pricing.currency` PLN, `basePrice` 64.90, `discountPercent` 12, `finalPrice` 57.11, `formatted.finalPrice` "57,11 zł"; `availability.status` IN_STOCK, `stockLevel` 42, `warehouse.city` "Modła Królewska", `expectedDelivery` = next working day (before 18:00 Warsaw) or the one after; label "Dostępny, dostawa …"; `personalization` PERSONALIZED / WORKSHOP / `preferredBrand` true; `meta.degraded` false; four sources OK | |
| TC-02: No customer | D; same without header | `personalization.status` STANDARD; `meta.sources.customer.status` SKIPPED; `discountPercent` 0, `finalPrice` 64.90; `degraded` false | |
| TC-03: Another market | D; `"$P/W6103?market=de-AT"` | `language` de; name "Ölfilter M+H"; EUR 14.90 formatted "€ 14,90"; warehouse Strullendorf; German labels ("Auf Lager, Lieferung …") | |
| TC-04: Language fallback | D; `"$P/TS19100KR?market=nl-BE"` | 200; `language` en; name "Seat top black PVC"; labels in Dutch; EUR 54.30 | |
| TC-05: Outside assortment | R; `"$P/10112PB?market=pl-PL"` | 404 `application/problem+json`; `code` PRODUCT_NOT_IN_MARKET; `correlationId` equals response header | |
| TC-06: Unknown product | `"$P/NOPE123?market=pl-PL"` | 404 `code` PRODUCT_NOT_FOUND | |
| TC-07: Unsupported market | `"$P/W6103?market=xx-XX"` | 400 `code` UNSUPPORTED_MARKET; `supportedMarkets` lists the 8 markets; `"$P/W6103?market=PL_pl"` → 200 (normalised) | |
| TC-08: **Pricing starts timing out** (interview scenario) — REQ-AGG-RES-005/014/015/016 | D; `curl -X PUT localhost:8081/chaos/pricing -d '{"mode":"TIMEOUT"}' -H 'Content-Type: application/json'`; run TC-01 20× printing `.meta.sources.pricing` and `time_total`; then `DELETE /chaos`, D again, wait 11 s; run TC-01 5× | Requests 1–10: 200, `pricing.status` UNAVAILABLE, label "Cena chwilowo niedostępna", source TIMEOUT, total ≈ 300 ms; requests 11–20: source CIRCUIT_OPEN, total < 150 ms, product and stock still shown; after reset + 11 s: first 3 requests are trials and return OK, prices back; all 5 show AVAILABLE | |
| TC-09: Availability down | R, D; `PUT /chaos/availability {"mode":"DOWN"}`; TC-01 | 200; `availability.status` UNKNOWN, label "Stan magazynowy nieznany"; `degraded` true; price present | |
| TC-10: Customer down | R, D; `PUT /chaos/customer {"mode":"DOWN"}`; TC-01 | 200; STANDARD; `discountPercent` 12 and `finalPrice` 57.11 still present (Pricing owns discounts); `degraded` true | |
| TC-11a: Catalog down | R, D; `PUT /chaos/catalog {"mode":"DOWN"}`; TC-01 × 7 printing status and `Retry-After` | Requests 1–5: 503 `CATALOG_UNAVAILABLE`, `Retry-After: 1`; requests 6–7: 503 with `Retry-After: 10` (circuit open, answered in < 50 ms) | |
| TC-11b: Catalog slow | `docker compose restart aggregator`; D; `PUT /chaos/catalog {"mode":"TIMEOUT"}`; TC-01 | 504 `CATALOG_TIMEOUT` in ≈ 250 ms | |
| TC-12: All optional sources hang | R, D; TIMEOUT for pricing, availability, customer; TC-01 | 200 in < 500 ms (≈ 350 ms); product shown; pricing UNAVAILABLE, availability UNKNOWN, personalization STANDARD; `degraded` true | |
| TC-13: Correlation and logs — REQ-OPS-OBS-001/003, RC-OPS-OBS-001 | D; TC-01 with `-H 'X-Correlation-Id: tc13-abc' -i`; `docker compose logs aggregator \| grep tc13-abc`; `docker compose logs aggregator \| grep -c C-1002` | response header and `meta.correlationId` = tc13-abc; exactly one `product_aggregated` line with tc13-abc, `customer=present`, four `source=STATUS/ms` pairs; grep count of C-1002 = 0 | |
| TC-14: gRPC — REQ-API-GRPC-001/002/003 | `grpcurl -plaintext localhost:9090 list`; `grpcurl -plaintext -d '{"product_id":"W6103","market":"pl-PL","customer_id":"C-1002"}' localhost:9090 kramp.aggregator.v1.ProductAggregator/GetProduct`; same with `NOPE123` | `kramp.aggregator.v1.ProductAggregator` listed (reflection); response mirrors TC-01 (final price "57.11"); NOPE123 → `Code: NotFound` | |
| TC-15: Demo page — REQ-DEMO-PAGE-001/002/003, RC-DEMO-PAGE-001 | open `http://localhost:8080/`; choose W6103 / pl-PL / C-1002; Pricing → TIMEOUT; "Fire 20 requests"; Reset | Kramp styling (red `#9B1915` header, Barlow fonts, text wordmark "KRAMP", tagline); card with product image, Polish name, price, stock, delivery, segment badge; source bar shows four statuses with ms; under chaos the price area shows the Polish label and the bar shows TIMEOUT then CIRCUIT_OPEN; footer states prices, stock and customers are fictitious | |
| TC-16: Operations — REQ-OPS-OBS-004/005/006 | R; `PUT /chaos/pricing {"mode":"DOWN"}`; TC-01 × 12; `curl localhost:8080/actuator/health/readiness`; `curl localhost:8080/actuator/health \| jq .components.upstreams`; `curl -s localhost:8080/actuator/prometheus \| grep -E 'upstream_call_seconds_count\|aggregation_result_total'` | readiness `UP`; `upstreams.details.pricing` OPEN, others CLOSED; both metric families present with `source`/`outcome` tags | |
| TC-17: Fresh clone — REQ-DLV-SUBMIT-001/002/007 | in an empty dir: `git clone <GitHub URL>`; `docker compose up --build`; separately (JDK 21) `./mvnw verify`; open the latest GitHub Actions run | both succeed without extra steps; CI run green; repo visible on GitHub | |
| TC-18: Hygiene before submission — RC-DEMO-PAGE-002, quality of README | read README end to end; run the hygiene scan kept outside the repository; `git ls-files \| grep -iE 'logo\|\.svg$\|\.env'` | README answers every Deliverables bullet and Design Question A clearly; scan finds nothing; no Kramp logo or env files tracked | |
| TC-19: Realistic background noise — REQ-MOCK-UPSTREAM-001/002 at system level (supplementary) | R (HEALTHY, PDF reliability); `for i in $(seq 200); do curl -s -H 'X-Customer-Id: C-1002' "$P/W6103?market=pl-PL" \| jq -r '.meta.sources.availability.status'; done \| sort \| uniq -c` | mostly OK; a few (~1–3%) non-OK for availability; catalog 503s are rare (~0.1% of requests, i.e. usually 0 in 200) | |

Results: _filled by the validator per TC (Pass / Fail + note)_
Checked by: _Dawid, date_ · Validated by: _Dawid, date_

## Approval
Every stage moved on only after the owner's approval. Decisions behind each gate: `docs/design/decision-log.md`.

| Gate | What was approved | Approved by | Date |
|---|---|---|---|
| G0 Scoping | stack (Java 21 + Spring Boot 4.1.1; Java 25 considered and rejected, D-01), Design Question A, gRPC as a second endpoint, AI disclosure in README; approach 1 of 3 (HTTP mocks + virtual threads + programmatic resilience4j) | Dawid Kłos | 2026-10-14 |
| G1 Design | five design sections (architecture & API · resilience · mocks · markets, observability & demo · tests & tooling), then the written design spec v1 | Dawid Kłos | 2026-10-14 |
| G2 Requirements | 11 user requirements → 72 system requirements with planned proofs; traceability gate and validation protocol | Dawid Kłos | 2026-10-14 |
| G3 Test specification | 73 automated cases with oracles + 20 validation steps; 8 design gaps P1–P8 accepted → design spec v2, 77 requirements | Dawid Kłos | 2026-10-14 |
| G4 Implementation plan | 20 tasks, each mapped to the AT cases it must make pass and the requirement rows it flips; five more gaps found while planning (P9–P13) decided | Dawid Kłos | 2026-10-14 |
| G5 Review | <!-- task 20: verdict from CR-001_review.md --> | | |
| G6 Validation | <!-- task 20: TC-01…TC-19 results signed --> | | |
| G7 Publish | <!-- task 20: hygiene clean, strict gate green, explicit go to push --> | | |

## Handover
State: in-progress · Artifacts: this repository · Guarding gate: `./mvnw verify -Dgate.strict=true` · Next: implementation per the approved plan (G4).
