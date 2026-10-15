# Demo page

Owns: REQ-DEMO-PAGE-*, RC-DEMO-PAGE-*

## What it does
- REQ-DEMO-PAGE-001 — The aggregator shall serve a Kramp-styled demo page at `/` showing the aggregated product.
- REQ-DEMO-PAGE-002 — The demo page shall show each upstream's status and duration.
- REQ-DEMO-PAGE-003 — The demo page shall switch mock chaos modes.
- REQ-DEMO-PAGE-004 — The demo page shall run each validation scenario TC-01…TC-12 with one click and show every response next to the expected outcome.
- REQ-DEMO-PAGE-005 — The demo page shall show the circuit-breaker state of each upstream.
- REQ-DEMO-PAGE-006 — When the product cannot be shown, the demo page shall tell the customer why in the market language and retry a 503 automatically after its Retry-After.

## Rules & invariants
- Colours `#9B1915` (primary, white text) and `#FF9E00` (secondary, black text), background `#F5F7FA`, text `#121E31`; fonts Barlow / Barlow Condensed; text wordmark "KRAMP", tagline "It's that easy".
- The page runs locally only; nothing is published.
- The scenario panel does not judge: it shows the expected outcome next to the raw responses, and a person records Pass or Fail in CR-001 § Validation. The curl steps in the test specification stay the reference.
- Every scenario starts from the same state: deterministic mode, every circuit CLOSED, 20 successful warm-up requests; it ends with a chaos reset.
- An error card shows the customer's message first and the technical problem collapsed below it; nothing is removed from the technical part.
- Only the person's own request retries automatically, at most 5 times per action; scenarios and "Fire 20 requests" never do, because extra requests would shift the breaker counts they show. Changing the selection or starting a run cancels a pending retry.

## Risk controls
- RC-DEMO-PAGE-001 — The demo page shall state that prices, stock and customers are fictitious.
- RC-DEMO-PAGE-002 — The repository shall not contain Kramp logo files.

## How it's proven
| ID | Resolved by | Proof | Status |
|----|-------------|-------|--------|
| REQ-DEMO-PAGE-001 | observed | TC-15 | ✅ |
| REQ-DEMO-PAGE-002 | observed | TC-15 | ✅ |
| REQ-DEMO-PAGE-003 | observed | TC-15 | ✅ |
| REQ-DEMO-PAGE-004 | observed | TC-15 | ✅ |
| REQ-DEMO-PAGE-005 | observed | TC-15 | ✅ |
| REQ-DEMO-PAGE-006 | observed | TC-15 | ✅ |
| RC-DEMO-PAGE-001 | observed | TC-15 | ✅ |
| RC-DEMO-PAGE-002 | observed | TC-18 | ✅ |

## Where the code lives
`aggregator/src/main/resources/static/index.html`.

## Connected
UR-11, UR-10.
