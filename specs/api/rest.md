# REST API

Owns: REQ-API-REST-*, RC-API-REST-*

## What it does
- REQ-API-REST-001 — The system shall serve `GET /api/v1/products/{productId}?market={market}` returning JSON.
- REQ-API-REST-002 — The system shall read the optional customer ID from the `X-Customer-Id` header.
- REQ-API-REST-003 — Error responses shall be RFC 9457 problem+json with `code` and `correlationId`.
- REQ-API-REST-004 — When `productId` is malformed, the system shall respond 400 INVALID_REQUEST.
- REQ-API-REST-005 — A 503 caused by an open Catalog circuit shall carry a `Retry-After` header.
- REQ-API-REST-006 — The system shall publish an OpenAPI description and Swagger UI.
- REQ-API-REST-007 — When `X-Customer-Id` is blank or malformed, the system shall respond 400 INVALID_REQUEST.

## Rules & invariants
- `productId` matches `[A-Za-z0-9-]{1,40}`; `X-Customer-Id`, when present, matches `[A-Za-z0-9-]{1,64}`. Nothing is sent upstream for an invalid request. A `customerId` query parameter is ignored: the contract is header-only.
- Error codes: UNSUPPORTED_MARKET (400, with `supportedMarkets`), INVALID_REQUEST (400, also for a missing `market`), PRODUCT_NOT_FOUND and PRODUCT_NOT_IN_MARKET (404), NOT_FOUND (404, unknown path), CATALOG_UNAVAILABLE (503), CATALOG_TIMEOUT (504), INTERNAL_ERROR (500, generic detail, never a stack trace).
- `Retry-After` on CATALOG_UNAVAILABLE is the configured open-state wait in seconds (an upper bound) when the circuit is open, otherwise 1.

## Risk controls
- RC-API-REST-001 — Upstream status codes and error bodies shall never appear in client responses.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-API-REST-001 | automated | AT-VIEW-01 · ProductRestApiIT#shouldReturnAggregatedViewWhenAllUpstreamsAnswer | ✅ |
| REQ-API-REST-002 | automated | AT-REST-02 · ProductRestApiIT#shouldPersonalizeWhenCustomerHeaderPresent | ✅ |
| REQ-API-REST-003 | automated | AT-REST-03 · ProductRestApiIT#shouldRenderProblemJsonWhenRequestFails | ✅ |
| REQ-API-REST-004 | automated | AT-REST-04 · ProductRestApiIT#shouldReturn400WhenProductIdMalformed | ✅ |
| REQ-API-REST-005 | automated | AT-REST-05 · ProductRestApiIT#shouldAdviseRetryAfterWhenCatalogCircuitOpen | ✅ |
| REQ-API-REST-006 | automated | AT-REST-06 · ProductRestApiIT#shouldPublishOpenApiWhenRequested | ✅ |
| REQ-API-REST-007 | automated | AT-ROB-02 · ProductViewServiceTest#shouldRejectWhenCustomerIdIsMalformed | ✅ |
| RC-API-REST-001 | automated | AT-REST-RC1 · ProductRestApiIT#shouldNeverEchoUpstreamDetailsWhenUpstreamFails | ✅ |

## Where the code lives
`aggregator/src/main/java/com/kramp/aggregator/api/rest/`, `…/presentation/ProductViewService.java`.

## Connected
UR-1, UR-4, UR-9; ADR-0004 (200 with section statuses), ADR-0005 (customer ID in a header).
