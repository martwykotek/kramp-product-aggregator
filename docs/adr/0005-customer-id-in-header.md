# ADR-0005: Customer id in the `X-Customer-Id` header

Status: accepted, 2026-10-14 · Decided by: Dawid (decision log D-07)

## Context
The customer id is optional input. It selects personal discounts and the customer's segment, and it is
personal data that must not end up in logs.

## Decision
Clients send it in the `X-Customer-Id` header (format `[A-Za-z0-9-]{1,64}`). A missing header means "no
customer" (Customer is skipped, standard prices); a blank or malformed one is rejected with 400 before any
upstream call. gRPC carries it as an `optional` field. The aggregator never logs it: the summary line says
`customer=present|absent`.

## Consequences
- The id stays out of URLs, access logs, browser history and shared caches keyed by URL.
- In production the id would come from the authenticated token rather than a header the client sets;
  the header stands in for that here.
- Pricing receives the id as a query parameter because that is the simulated upstream's contract, so
  upstream URLs are never logged (failure messages are built from the source name and status only).

## Alternatives
- **Query parameter**: simplest to try with curl, but leaks into every log and cache along the way.
- **Path segment** (`/customers/{id}/products/{productId}`): same leak, and a different resource shape
  for the same product.
