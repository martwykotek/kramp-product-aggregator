#!/usr/bin/env bash
# The interview scenarios in a terminal, against `docker compose up`: a customer in Poland (TC-01),
# no customer (TC-02), language fallback (TC-04), a product outside the market (TC-05) and
# "Pricing starts timing out" (TC-08). Steps and expected results: docs/verification/test-specification.md §5.2.
set -euo pipefail

P=http://localhost:8080/api/v1/products
HEALTH=http://localhost:8080/actuator/health
CHAOS=http://localhost:8081/chaos
SOURCES=(catalog pricing availability customer)

for tool in curl jq; do
  command -v "$tool" >/dev/null || { echo "demo.sh needs $tool on the PATH" >&2; exit 1; }
done

tmp=$(mktemp -d)
# Leave the mocks as they were found (HEALTHY), also when a step fails.
trap 'curl -s -X DELETE "$CHAOS" >/dev/null || true; rm -rf "$tmp"' EXIT

section() { printf '\n== %s\n' "$1"; printf '   expected: %s\n' "$2"; }

chaos() { curl -sf -X PUT "$CHAOS/$1" -H 'Content-Type: application/json' -d "$2" >/dev/null; }

# Mode D: realistic latency, random failures off.
deterministic() { for s in "${SOURCES[@]}"; do chaos "$s" '{"mode":"FLAKY","reliability":1.0}'; done; }

breakers() { curl -sf "$HEALTH" | jq -c '.components.upstreams.details'; }

all_closed() { curl -sf "$HEALTH" | jq -e '[.components.upstreams.details[]] | all(. == "CLOSED")' >/dev/null; }

countdown() {
  for ((s = $1; s > 0; s--)); do printf '\r   waiting %2d s ' "$s"; sleep 1; done
  printf '\r%20s\r' ''
}

# request <product> <market> [customer]: writes the body to $tmp/body, the headers to $tmp/headers,
# and prints "<http status> <total ms>".
request() {
  local customer=()
  [[ -n ${3:-} ]] && customer=(-H "X-Customer-Id: $3")
  # The ${a[@]+...} form keeps an empty array legal under `set -u` in the bash 3.2 that macOS ships.
  curl -s ${customer[@]+"${customer[@]}"} -D "$tmp/headers" -o "$tmp/body" -w '%{http_code} %{time_total}\n' \
    "$P/$1?market=$2" | LC_ALL=C awk '{ printf "%s %d\n", $1, $2 * 1000 }'
}

# The same preparation as the demo page's scenario panel: deterministic mode, every circuit CLOSED,
# then 20 successful calls, so a failing source opens its circuit after exactly 10 failed calls.
prepare() {
  deterministic
  if ! all_closed; then
    echo "   a circuit is not CLOSED: $(breakers); it half-opens 10 s after opening"
    countdown 11
    for _ in $(seq 10); do
      all_closed && break
      request W6103 pl-PL C-1002 >/dev/null
    done
    all_closed || { echo "   circuits still not CLOSED: run 'docker compose restart aggregator'" >&2; exit 1; }
  fi
  for _ in $(seq 20); do request W6103 pl-PL C-1002 >/dev/null; done
  echo "   circuits: $(breakers)"
}

pricing_row() {  # pricing_row <n>: one line per TC-08 request
  local status ms
  read -r status ms < <(request W6103 pl-PL C-1002)
  jq -r --arg n "$1" --arg status "$status" --arg ms "$ms" \
    '"\($n)\tHTTP \($status)\t\($ms) ms\tpricing \(.pricing.status)\tsource \(.meta.sources.pricing.status)/\(.meta.sources.pricing.durationMs) ms\t\(.pricing.label // .pricing.formatted.finalPrice)"' \
    "$tmp/body"
}

VIEW='{language, name: .product.name,
  pricing: (.pricing | {status, currency, basePrice, discountPercent, finalPrice, formatted: .formatted.finalPrice, label}),
  availability: (.availability | {status, stockLevel, warehouse: .warehouse.city, expectedDelivery, label}),
  personalization, degraded: .meta.degraded,
  sources: (.meta.sources | map_values("\(.status)/\(.durationMs) ms"))}'

section "Preparing: deterministic mode, circuits CLOSED, 20 warm-up requests" "all circuits CLOSED"
prepare

section "TC-01 A customer in Poland: W6103, pl-PL, X-Customer-Id C-1002" \
  "200; pl; PLN 64.90 - 12 % = 57.11 (\"57,11 zł\"); IN_STOCK 42, Modła Królewska; PERSONALIZED WORKSHOP; not degraded"
request W6103 pl-PL C-1002
jq "$VIEW" "$tmp/body"

section "TC-02 No customer: W6103, pl-PL" "STANDARD; customer SKIPPED; discount 0, final 64.90; not degraded"
request W6103 pl-PL
jq "$VIEW" "$tmp/body"

section "TC-04 Language fallback: TS19100KR, nl-BE" "200; language en (\"Seat top black PVC\"); Dutch labels; EUR 54.30"
request TS19100KR nl-BE
jq "$VIEW" "$tmp/body"

section "TC-05 Outside the market's assortment: 10112PB, pl-PL" \
  "404 application/problem+json; PRODUCT_NOT_IN_MARKET; correlationId equals the response header"
request 10112PB pl-PL
header_id=$(grep -i '^x-correlation-id:' "$tmp/headers" | cut -d' ' -f2 | tr -d '\r')
content_type=$(grep -i '^content-type:' "$tmp/headers" | cut -d' ' -f2 | tr -d '\r')
jq --arg header "$header_id" --arg type "$content_type" \
  '{contentType: $type, status, code, detail, correlationId, sameAsHeader: (.correlationId == $header)}' "$tmp/body"

section "TC-08 Pricing starts timing out: Pricing in TIMEOUT mode, 20 requests" \
  "1-10 pricing UNAVAILABLE, source TIMEOUT, ≈ 300 ms; 11-20 CIRCUIT_OPEN in ≈ 0 ms (no call to Pricing), total without Pricing's 300 ms wait; product and stock still shown"
chaos pricing '{"mode":"TIMEOUT"}'
for i in $(seq 20); do pricing_row "$i"; done
echo "   circuits: $(breakers)"

section "TC-08 Pricing recovers: chaos reset, deterministic mode, 11 s, 5 requests" \
  "the first 3 are half-open trials and OK; all 5 show the price (AVAILABLE)"
curl -s -X DELETE "$CHAOS" >/dev/null
deterministic
countdown 11
for i in $(seq 5); do pricing_row "$i"; done
echo "   circuits: $(breakers)"

printf '\nDone. The mocks are reset to HEALTHY on exit.\n'
