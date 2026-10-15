# ADR-0003: Time budgets, one budgeted retry and a circuit breaker per source

Status: accepted, 2026-10-14

## Context
Customers on slow rural connections need a bounded response time. Catalog is required; Pricing,
Availability and Customer are optional. A slow or failing optional upstream must neither stretch the
response nor be hammered while it is down.

## Decision
- **Budgets per source** (Catalog 250, Pricing 300, Availability 350, Customer 200 ms) and a 500 ms global
  deadline. A budget covers every attempt of its source. Values are validated at start-up; the service
  refuses to start with an invalid configuration.
- **One retry**, only after a fast failure (a 5xx answer) and only when the remaining budget is at least
  the source's typical latency. Never after a timeout, a not-found answer or an unusable body. A
  connection refused or reset is not retried by us: the JDK client already resends an idempotent request
  once.
- **One circuit breaker per source** (resilience4j, used programmatically): count-based window of 20
  calls, minimum 10, 50 % failure threshold, 10 s open, 3 half-open trial calls. Not-found answers do not
  count. **Calls the aggregator cancels because Catalog already failed release their permission without
  counting as failures** (P8): otherwise a Catalog outage would open the other breakers and keep prices
  hidden after Catalog recovers.
- The orchestrator waits budget + 25 ms, so the HTTP client's own timeout fires first and is recorded.

## Consequences
- Worst case with all optional sources hanging ≈ 350 ms; with Catalog hanging, 504 at ≈ 250 ms.
- When Pricing times out, about ten requests pay the 300 ms, then the breaker answers in under a
  millisecond and probes again every 10 s.
- `GuardedSource` is the one place where breaker, retry and cancellation meet; it is tested with an
  injected clock and a fake nano clock.

## Alternatives
- **Annotations (`@CircuitBreaker`, `@Retry`) or Spring Cloud Circuit Breaker**: less code, but the
  budget-aware retry and the cancellation rule do not fit an annotation, and the behaviour is hidden.
- **Failsafe** or a hand-written breaker: workable; resilience4j brings Micrometer metrics and a
  testable clock.
- **Retry on every failure**: doubles the load on an upstream that is already struggling.
