# Validation report

Validated by Dawid Kłos on 15 October 2026, before publication, against the stack started with
`docker compose up --build`. Automated tests (unit, integration, architecture and the requirements
traceability gate) run in `./mvnw verify` on every build and in CI; this report covers what was checked
by hand on the running system.

- **TC-01…TC-12**: one click each in the demo page's *Validation scenarios* panel. Every run starts from
  the same state (mocks deterministic, all circuits closed, 20 warm-up requests).
- **TC-13…TC-19**: terminal (`curl`, `jq`, `grpcurl`) and the demo page.
- Requests for every case: [`aggregator.http`](aggregator.http) and [`scripts/demo.sh`](../../scripts/demo.sh).

| TC | What was checked | Result | Evidence |
|---|---|---|---|
| TC-01 | Customer in Poland (`W6103`, `pl-PL`, `C-1002`) | Pass | 200; "Filtr oleju Mann & Hummel"; 64,90 zł − 12 % = **57,11 zł**; 42 in stock at Modła Królewska, delivery 16 Oct 2026; WORKSHOP, preferred brand; four sources OK; not degraded |
| TC-02 | No customer | Pass | 64,90 zł, discount 0; customer SKIPPED; STANDARD; not degraded |
| TC-03 | Another market (`de-AT`) | Pass | "Ölfilter M+H"; € 14,90; 310 at Strullendorf; "Auf Lager, Lieferung 16.10.2026" |
| TC-04 | Language fallback (`TS19100KR`, `nl-BE`) | Pass | content in English ("language": "en"), labels in Dutch ("Op voorraad, levering 16 okt 2026"); € 54,30 |
| TC-05 | Product outside the market (`10112PB`, `pl-PL`) | Pass | 404 `PRODUCT_NOT_IN_MARKET`, `application/problem+json`; correlation id in body = response header |
| TC-06 | Unknown product | Pass | 404 `PRODUCT_NOT_FOUND` |
| TC-07 | Unsupported market | Pass | `xx-XX` → 400 `UNSUPPORTED_MARKET` listing the 8 markets; `PL_pl` → 200 (normalised) |
| TC-08 | **Pricing starts timing out** | Pass | requests 1–10: price "Cena chwilowo niedostępna", Pricing TIMEOUT ≈ 305 ms, total ≈ 314 ms; 11–20: Pricing CIRCUIT_OPEN in 0 ms, total 77–185 ms, product and stock still shown; after recovery and 10 s: 5 × OK, prices back |
| TC-09 | Availability down | Pass | 200; "Stan magazynowy nieznany"; price present; degraded |
| TC-10 | Customer down | Pass | 200; STANDARD; discount 12 % and 57,11 zł kept (Pricing owns discounts); degraded |
| TC-11a | Catalog down | Pass | requests 1–5: 503 `CATALOG_UNAVAILABLE`, `Retry-After: 1` (16–22 ms); 6–7: 503, `Retry-After: 10` in 2 ms (circuit open) |
| TC-11b | Catalog slow | Pass | 504 `CATALOG_TIMEOUT` in 261 ms |
| TC-12 | All optional sources hang | Pass | 200 in 361 ms; product shown; price unavailable, stock unknown, STANDARD; degraded |
| TC-13 | Correlation id and logs | Pass | `X-Correlation-Id: tc13-dawid` echoed in header and body; exactly one `product_aggregated` line with it (`customer=present`, four sources with status and time); the customer id appears in the logs 0 times |
| TC-14 | gRPC | Pass | service listed by reflection; `GetProduct` returns the TC-01 view (final price "57.11"); `NOPE123` → `NotFound` |
| TC-15 | Demo page | Pass | Kramp styling, text wordmark, product image, Polish name, per-source timeline, circuit states, footer stating fictitious data; Catalog down → "Produkt chwilowo niedostępny. Spróbujemy ponownie za 5 s.", the product came back by itself after the mocks were reset; "Technical details" show code, `Retry-After` and correlation ids; `10112PB` → "Ten produkt nie jest dostępny w Twoim kraju" without retry |
| TC-16 | Operations | Pass | with Pricing down: readiness `UP`, `upstreams` shows pricing OPEN and the others CLOSED; `upstream_call_seconds{source,outcome}` and `aggregation_result_total{outcome}` exported |
| TC-17 | Fresh clone and CI | Pass | clone from GitHub: `docker compose up --build` healthy, the TC-01 request answers 57,11 zł with four sources OK; `./mvnw verify` green with JDK 21; GitHub Actions CI green |
| TC-18 | Repository hygiene and README | Pass | no logo, `.svg` or `.env` files; README read in full against the assignment |
| TC-19 | Realistic noise (200 requests, mocks in their default random mode) | Pass | Catalog 200 × OK; Pricing 199 OK / 1 TIMEOUT; Availability 198 OK / 2 TIMEOUT; Customer 199 OK / 1 TIMEOUT; no request without a product |

Fast upstream errors are retried once within the source's budget, so what a client sees is mostly the
hangs: every non-OK result in TC-19 is a TIMEOUT.
