# Chaos API

Owns: REQ-MOCK-CHAOS-*

## What it does
- REQ-MOCK-CHAOS-001 — `PUT /chaos/{service}` shall switch a mock to HEALTHY, SLOW, TIMEOUT, DOWN or FLAKY.
- REQ-MOCK-CHAOS-002 — `DELETE /chaos` shall restore every mock to its stated reliability profile.
- REQ-MOCK-CHAOS-003 — `GET /chaos` shall report each mock's current mode.

## Rules & invariants
- SLOW multiplies latency by `slowFactor` (default 5); TIMEOUT hangs every call 2–5 s, then answers 503; DOWN answers 503 immediately; FLAKY overrides reliability (default 0.5). FLAKY with reliability 1.0 is the deterministic mode used by the validation protocol.
- CORS allows `http://localhost:8080` so the demo page can switch modes.

## Risk controls
None in this leaf.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-MOCK-CHAOS-001 | automated | AT-CHAOS-01 · ChaosApiIT#shouldReportModeWhenModeIsSet | ❌ no evidence — GAP |
| REQ-MOCK-CHAOS-002 | automated | AT-CHAOS-02 · ChaosApiIT#shouldRestoreEveryUpstreamWhenReset | ❌ no evidence — GAP |
| REQ-MOCK-CHAOS-003 | automated | AT-CHAOS-01 · ChaosApiIT#shouldReportModeWhenModeIsSet | ❌ no evidence — GAP |

## Where the code lives
`upstream-mocks/src/main/java/com/kramp/mocks/chaos/`, `…/behavior/`.

## Connected
UR-11; TC-08…TC-12.
