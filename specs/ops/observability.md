# Observability and operations

Owns: REQ-OPS-OBS-*, RC-OPS-OBS-*

## What it does
- REQ-OPS-OBS-001 — The system shall echo `X-Correlation-Id`, generating one when absent.
- REQ-OPS-OBS-002 — The system shall forward the correlation ID to every upstream call.
- REQ-OPS-OBS-003 — The system shall log one summary line per request with each upstream's status and duration.
- REQ-OPS-OBS-004 — The system shall expose per-upstream call timers and aggregation outcome counters for Prometheus.
- REQ-OPS-OBS-005 — Liveness and readiness shall report UP while upstreams are failing.
- REQ-OPS-OBS-006 — Health details shall report each upstream's circuit breaker state.

## Rules & invariants
- A client correlation id is accepted when it matches `[A-Za-z0-9._-]{1,64}`; otherwise a UUID is generated.
- Log line: `product_aggregated productId=… market=… customer=present|absent outcome=OK|DEGRADED|FAILED durationMs=… catalog=OK/48 …`.
- Metrics: timer `upstream.call{source,outcome}`, counter `aggregation.result{outcome}`, resilience4j circuit-breaker metrics.
- The `upstreams` health contributor is always UP and is not part of the liveness or readiness groups.

## Risk controls
- RC-OPS-OBS-001 — Logs shall never contain customer IDs.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-OPS-OBS-001 | automated | AT-OBS-01 · CorrelationIdFilterTest#shouldEchoWhenHeaderValid, #shouldGenerateWhenHeaderMissingOrInvalid | ❌ no evidence — GAP |
| REQ-OPS-OBS-002 | automated | AT-OBS-02 · ProductRestApiIT#shouldForwardCorrelationIdWhenCallingUpstreams | ❌ no evidence — GAP |
| REQ-OPS-OBS-003 | automated | AT-OBS-03 · AggregationTelemetryTest#shouldLogOneSummaryLineWhenAggregationCompletes | ❌ no evidence — GAP |
| REQ-OPS-OBS-004 | automated | AT-OBS-04 · AggregationTelemetryTest#shouldRecordMetricsWhenAggregationCompletes, ActuatorIT#shouldExposeAggregationMetricsWhenScraped | ❌ no evidence — GAP |
| REQ-OPS-OBS-005 | automated | AT-OBS-05 · ActuatorIT#shouldStayReadyWhenUpstreamsFail | ❌ no evidence — GAP |
| REQ-OPS-OBS-006 | automated | AT-OBS-06 · ActuatorIT#shouldReportBreakerStatesWhenHealthQueried | ❌ no evidence — GAP |
| RC-OPS-OBS-001 | automated | AT-OBS-RC1 · AggregationTelemetryTest#shouldNotLogCustomerIdWhenCustomerPresent, ProductRestApiIT#shouldKeepCustomerIdOutOfLogsWhenRequestFails | ❌ no evidence — GAP |

## Where the code lives
`aggregator/src/main/java/com/kramp/aggregator/observability/`, `aggregator/src/main/resources/application.yml`.

## Connected
UR-7; README "How I would operate it".
