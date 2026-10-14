# ADR-0002: Upstream mocks as a separate HTTP process

Status: accepted, 2026-10-14 · Decided by: Dawid (decision log D-05, D-09)

## Context
The assignment says the mocks matter: they must show realistic latency and the stated reliability.
In-process fakes that `sleep()` would never exercise connection handling, read timeouts, serialization
or the behaviour of a client whose server stops answering mid-request.

## Decision
`upstream-mocks` is its own Spring Boot application (port 8081) serving Catalog, Pricing, Availability
and Customer over real HTTP. Latency is log-normal around the assignment's typical latency (p99 ≈ 3 ×
median); failures happen at 1 − reliability and split into fast 503, slow 500 and a 2–5 s hang; the
random sequence is seedable (`MOCKS_SEED`). A Chaos API (`/chaos`) switches each upstream to SLOW,
TIMEOUT, DOWN or FLAKY at runtime. Each mock reports errors in its own shape, as different teams would.

## Consequences
- Timeouts, retries and circuit breakers are proven against real sockets, in the demo and in validation.
- The failure scenarios can be shown live from the demo page or `scripts/demo.sh`.
- Automated tests do not depend on the mocks: aggregator tests use WireMock with fixed delays, faults
  and malformed bodies, so they stay deterministic.
- Two containers instead of one; `docker compose up` starts both, with the aggregator waiting for the
  mocks' health check.

## Alternatives
- **In-process fakes**: simplest, but they hide transport behaviour.
- **WireMock as the runtime mock**: good for fixed stubs, awkward for a statistical latency model and a
  live chaos switch.
