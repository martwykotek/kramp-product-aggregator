# ADR-0004: HTTP 200 with a status per section for partial data

Status: accepted, 2026-10-14 · Decided by: Dawid (decision log D-06)

## Context
The assignment defines what happens when each upstream fails: no Catalog means no response; without
Pricing the product is shown with price unavailable; without Availability stock is unknown; without
Customer the response is standard. Clients have to render all of these cases.

## Decision
Whenever Catalog returned the product, the answer is **200** and each section carries its own status:
`pricing.status` (`AVAILABLE` | `UNAVAILABLE` + localized label), `availability.status` (`IN_STOCK` |
`OUT_OF_STOCK` | `UNKNOWN`), `personalization.status` (`PERSONALIZED` | `STANDARD`). `meta.degraded`
says that a called optional source failed and `meta.sources` gives each source's status and duration.
Errors (400, 404, 503, 504) are reserved for "the product cannot be shown" and use
`application/problem+json` with a stable `code` and the correlation id. A not-found answer from an
optional source (no price in the market, no stock record, unknown customer) is a business outcome and
does not degrade the response.

## Consequences
- One contract for every client; a partial page needs no special status handling.
- Caches and monitoring must look at `meta.degraded`, not only at the status code; the operations section
  of the README alerts on the share of degraded responses.

## Alternatives
- **206 Partial Content**: meant for byte ranges, and clients rarely handle it.
- **An error on any upstream failure**: contradicts the assignment's constraints.
- **Omitting failed sections**: the client cannot tell "unavailable" from "not applicable".
