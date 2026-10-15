# gRPC API

Owns: REQ-API-GRPC-*

## What it does
- REQ-API-GRPC-001 — The system shall expose gRPC `GetProduct` returning the same aggregated view as REST.
- REQ-API-GRPC-002 — gRPC failures shall map to INVALID_ARGUMENT, NOT_FOUND, UNAVAILABLE or DEADLINE_EXCEEDED like REST statuses, and unexpected failures to INTERNAL like REST's 500.
- REQ-API-GRPC-003 — The gRPC server shall offer server reflection.

## Rules & invariants
- Service `kramp.aggregator.v1.ProductAggregator` on port 9090; correlation id in metadata `x-correlation-id`.
- Money is transported as decimal strings to avoid floating point; statuses are the same strings as in JSON.
- Mapping: 400 → INVALID_ARGUMENT, 404 → NOT_FOUND, 503 → UNAVAILABLE, 504 → DEADLINE_EXCEEDED, 500 → INTERNAL (generic description, logged as `request_failed_unexpectedly`). Descriptions never carry upstream text.
- gRPC calls are served on virtual threads, like REST requests.

## Risk controls
None in this leaf.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-API-GRPC-001 | automated | AT-GRPC-01 · ProductGrpcIT#shouldReturnSameViewAsRestWhenAllUpstreamsAnswer | ✅ |
| REQ-API-GRPC-002 | automated | AT-GRPC-02 · ProductGrpcIT#shouldMapFailureToGrpcStatusWhenRequestFails, #shouldAnswerInternalAndLogOnceWhenViewServiceFailsUnexpectedly | ✅ |
| REQ-API-GRPC-003 | observed | TC-14 (grpcurl list) | ✅ |

## Where the code lives
`aggregator/src/main/proto/product_aggregator.proto`, `aggregator/src/main/java/com/kramp/aggregator/api/grpc/`.

## Connected
UR-9, UR-10.
