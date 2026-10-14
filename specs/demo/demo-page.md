# Demo page

Owns: REQ-DEMO-PAGE-*, RC-DEMO-PAGE-*

## What it does
- REQ-DEMO-PAGE-001 — The aggregator shall serve a Kramp-styled demo page at `/` showing the aggregated product.
- REQ-DEMO-PAGE-002 — The demo page shall show each upstream's status and duration.
- REQ-DEMO-PAGE-003 — The demo page shall switch mock chaos modes.

## Rules & invariants
- Colours `#9B1915` (primary, white text) and `#FF9E00` (secondary, black text), background `#F5F7FA`, text `#121E31`; fonts Barlow / Barlow Condensed; text wordmark "KRAMP", tagline "It's that easy".
- The page runs locally only; nothing is published.

## Risk controls
- RC-DEMO-PAGE-001 — The demo page shall state that prices, stock and customers are fictitious.
- RC-DEMO-PAGE-002 — The repository shall not contain Kramp logo files.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-DEMO-PAGE-001 | observed | TC-15 | ❌ no evidence — GAP |
| REQ-DEMO-PAGE-002 | observed | TC-15 | ❌ no evidence — GAP |
| REQ-DEMO-PAGE-003 | observed | TC-15 | ❌ no evidence — GAP |
| RC-DEMO-PAGE-001 | observed | TC-15 | ❌ no evidence — GAP |
| RC-DEMO-PAGE-002 | observed | TC-18 | ❌ no evidence — GAP |

## Where the code lives
`aggregator/src/main/resources/static/index.html`.

## Connected
UR-11, UR-10.
