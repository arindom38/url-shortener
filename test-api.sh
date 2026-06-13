#!/usr/bin/env bash
set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"
PASS=0
FAIL=0

green="\033[0;32m"
red="\033[0;31m"
yellow="\033[0;33m"
bold="\033[1m"
reset="\033[0m"

pass() { echo -e "  ${green}✓${reset} $1"; ((PASS++)); }
fail() { echo -e "  ${red}✗${reset} $1"; ((FAIL++)); }
section() { echo -e "\n${bold}${yellow}▶ $1${reset}"; }

expect_status() {
  local label="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "$label → HTTP $actual"
  else
    fail "$label → expected HTTP $expected, got HTTP $actual"
  fi
}

echo -e "${bold}URL Shortener API Tests${reset} → $BASE_URL"

# ── 1. Shorten a valid URL ───────────────────────────────────────────────────
section "POST /api/urls — shorten valid URL"

response=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.example.com/some/very/long/path?query=1"}')

body=$(echo "$response" | sed '$d')
status=$(echo "$response" | tail -n1)

expect_status "create short URL" 201 "$status"

SHORT_CODE=$(echo "$body" | grep -o '"shortCode":"[^"]*"' | cut -d'"' -f4)
SHORT_URL=$(echo "$body" | grep -o '"shortUrl":"[^"]*"' | cut -d'"' -f4)
LONG_URL=$(echo "$body" | grep -o '"longUrl":"[^"]*"' | cut -d'"' -f4)

if [[ -n "$SHORT_CODE" ]]; then
  pass "shortCode present → \"$SHORT_CODE\""
else
  fail "shortCode missing in response"
fi

if [[ "$SHORT_URL" == *"$SHORT_CODE"* ]]; then
  pass "shortUrl contains shortCode → $SHORT_URL"
else
  fail "shortUrl malformed → $SHORT_URL"
fi

if [[ "$LONG_URL" == "https://www.example.com/some/very/long/path?query=1" ]]; then
  pass "longUrl echoed back correctly"
else
  fail "longUrl mismatch → $LONG_URL"
fi

# ── 2. Shorten a second URL (counter increments) ────────────────────────────
section "POST /api/urls — second URL gets different short code"

response2=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.github.com"}')

body2=$(echo "$response2" | sed '$d')
status2=$(echo "$response2" | tail -n1)
SHORT_CODE2=$(echo "$body2" | grep -o '"shortCode":"[^"]*"' | cut -d'"' -f4)

expect_status "create second short URL" 201 "$status2"

if [[ -n "$SHORT_CODE2" && "$SHORT_CODE2" != "$SHORT_CODE" ]]; then
  pass "second shortCode is different → \"$SHORT_CODE2\""
else
  fail "second shortCode same as first or missing → \"$SHORT_CODE2\""
fi

# ── 3. Redirect to original URL ─────────────────────────────────────────────
section "GET /{shortCode} — redirect to original URL"

if [[ -n "$SHORT_CODE" ]]; then
  redirect_status=$(curl -s -o /dev/null -w "%{http_code}" \
    --max-redirs 0 "$BASE_URL/$SHORT_CODE")
  expect_status "redirect returns 302" 302 "$redirect_status"

  location=$(curl -s -o /dev/null -w "%{redirect_url}" \
    --max-redirs 0 "$BASE_URL/$SHORT_CODE")
  if [[ "$location" == "https://www.example.com/some/very/long/path?query=1" ]]; then
    pass "Location header → $location"
  else
    fail "Location header mismatch → \"$location\""
  fi
else
  fail "skipped redirect test (no shortCode from step 1)"
fi

# ── 4. Redirect — unknown short code ────────────────────────────────────────
section "GET /{shortCode} — unknown code returns 404"

not_found_status=$(curl -s -o /dev/null -w "%{http_code}" \
  --max-redirs 0 "$BASE_URL/zzz999xyz")
expect_status "unknown short code" 404 "$not_found_status"

# ── 5. Shorten — blank URL ───────────────────────────────────────────────────
section "POST /api/urls — validation: blank URL"

blank_status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":""}')
expect_status "blank longUrl rejected" 400 "$blank_status"

# ── 6. Shorten — invalid URL format ─────────────────────────────────────────
section "POST /api/urls — validation: not a URL"

invalid_status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"not-a-valid-url"}')
expect_status "invalid URL format rejected" 400 "$invalid_status"

# ── 7. Shorten — missing field ───────────────────────────────────────────────
section "POST /api/urls — validation: missing longUrl field"

missing_status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/urls" \
  -H "Content-Type: application/json" \
  -d '{}')
expect_status "missing longUrl field rejected" 400 "$missing_status"

# ── 8. Cache hit — second redirect call ─────────────────────────────────────
section "GET /{shortCode} — cache hit on second call"

if [[ -n "$SHORT_CODE" ]]; then
  location2=$(curl -s -o /dev/null -w "%{redirect_url}" \
    --max-redirs 0 "$BASE_URL/$SHORT_CODE")
  if [[ "$location2" == "https://www.example.com/some/very/long/path?query=1" ]]; then
    pass "second redirect (cache hit) → same location"
  else
    fail "second redirect returned different location → \"$location2\""
  fi
else
  fail "skipped cache hit test"
fi

# ── Summary ──────────────────────────────────────────────────────────────────
echo ""
echo -e "${bold}Results: ${green}${PASS} passed${reset}  ${red}${FAIL} failed${reset}"

[[ $FAIL -eq 0 ]] && exit 0 || exit 1
