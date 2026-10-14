# Submission

Owns: REQ-DLV-SUBMIT-*

## What it does
- REQ-DLV-SUBMIT-001 — `docker compose up` shall start mocks and aggregator with Docker as the only prerequisite.
- REQ-DLV-SUBMIT-002 — `./mvnw verify` shall build and test everything with only JDK 21 installed.
- REQ-DLV-SUBMIT-003 — The README shall cover running, decisions and trade-offs, future work and Design Question A.
- REQ-DLV-SUBMIT-004 — The README shall walk through the pl-PL request and the Pricing-timeout scenario.
- REQ-DLV-SUBMIT-005 — The README shall state which data is real and which is fictitious.
- REQ-DLV-SUBMIT-006 — The README shall disclose the use of AI tools.
- REQ-DLV-SUBMIT-007 — The code shall be published in a GitHub repository.

## Rules & invariants
- Required README headings are listed in `quality-gates/src/test/java/com/kramp/quality/ReadmeContractTest.java`.
- TC-17 has a local part (fresh clone: `docker compose up --build` and `./mvnw verify`, proves 001 and 002) and a GitHub part after publication (repository visible, CI green, proves 007).

## Risk controls
None in this leaf.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-DLV-SUBMIT-001 | observed | TC-17 (local part) | ❌ no evidence — GAP |
| REQ-DLV-SUBMIT-002 | observed | TC-17 (local part) | ❌ no evidence — GAP |
| REQ-DLV-SUBMIT-003 | automated | AT-DLV-01 · ReadmeContractTest#shouldContainRequiredSectionsWhenReadmeChecked | ❌ no evidence — GAP |
| REQ-DLV-SUBMIT-004 | automated | AT-DLV-01 · ReadmeContractTest#shouldContainRequiredSectionsWhenReadmeChecked | ❌ no evidence — GAP |
| REQ-DLV-SUBMIT-005 | automated | AT-DLV-01 · ReadmeContractTest#shouldContainRequiredSectionsWhenReadmeChecked | ❌ no evidence — GAP |
| REQ-DLV-SUBMIT-006 | automated | AT-DLV-01 · ReadmeContractTest#shouldContainRequiredSectionsWhenReadmeChecked | ❌ no evidence — GAP |
| REQ-DLV-SUBMIT-007 | observed | TC-17 (GitHub part) | ❌ no evidence — GAP |

## Where the code lives
`README.md`, `docker-compose.yml`, `.github/workflows/ci.yml`.

## Connected
UR-10, UR-11.
