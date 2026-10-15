# Specs — Product Information Aggregator

Requirements come from the Kramp backend assignment. The assignment's needs are recorded as user
requirements (UR-n) in `.changes/CR-001_product-aggregator.md`. Each leaf below owns a namespace of
system requirements (REQ) and risk controls (RC) and states how each one is proven.

A test proves a requirement with a `// proves: REQ-…` comment directly above `@Test`.
`TraceabilityGate` (module `quality-gates`) runs in `./mvnw verify` and fails the build when a claimed
proof does not resolve; `-Dgate.strict=true` additionally fails on any open gap.

| Leaf | Owns | Covers |
|---|---|---|
| `aggregation/product-view.md` | REQ-AGG-VIEW-* | what one call returns |
| `aggregation/market.md` | REQ-AGG-MARKET-* | market, language, currency |
| `aggregation/resilience.md` | REQ-AGG-RES-*, RC-AGG-RES-* | partial failure, budgets, retry, circuit breaking |
| `api/rest.md` | REQ-API-REST-*, RC-API-REST-* | REST contract and errors |
| `api/grpc.md` | REQ-API-GRPC-* | gRPC contract |
| `ops/observability.md` | REQ-OPS-OBS-*, RC-OPS-OBS-* | correlation, logs, metrics, health |
| `mocks/upstreams.md` | REQ-MOCK-UPSTREAM-* | realistic upstream simulation |
| `mocks/chaos.md` | REQ-MOCK-CHAOS-* | failure injection for demos |
| `demo/demo-page.md` | REQ-DEMO-PAGE-*, RC-DEMO-PAGE-* | branded demo page |
| `delivery/submission.md` | REQ-DLV-SUBMIT-* | running locally, README, GitHub |

- ID grammar: `^(REQ|RC)-[A-Z]+(-[A-Z_]+)*-\d{3}$`. IDs are immutable; a dropped one becomes `de-scoped`.
- Evidence kinds (`Resolved by`): `automated` · `observed` (a TC step in CR-001 § Validation) · `covered-by <ID>`.
- Statuses: `✅` · `⚠️ <limitation>` · `❌ no evidence — GAP` · `de-scoped`.
- What earns a requirement: an externally observable commitment (client, operator, reviewer). Internal structure (package layering, formatting) is guarded by tests without IDs.
- Automated test cases (AT-…) are named in each leaf's proof table; the validation protocol (TC-…) is in `.changes/CR-001_product-aggregator.md` § Validation, summarised in `docs/verification/validation-report.md`.
