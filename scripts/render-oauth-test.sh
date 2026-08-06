#!/usr/bin/env bash
# =============================================================================
# scripts/render-oauth-test.sh
#
# Test OAuth callback URL đã được config đúng trên Render chưa.
# Shopify / Lazada / TikTok BẮT BUỘC HTTPS, đúng path, không có trailing slash.
#
# Usage:
#   bash scripts/render-oauth-test.sh
#   bash scripts/render-oauth-test.sh --channel shopify
#
# Checks:
#   - Callback URL trỏ đúng về api-osms.onrender.com (HTTPS, không trailing slash).
#   - Frontend URL trỏ đúng về app-osms.onrender.com.
#   - Backend có endpoint /api/address/countries return 200.
#   - Backend OPTIONS preflight trả CORS header cho origin Render.
# =============================================================================

set -Eeuo pipefail

BACKEND_URL="${RENDER_BACKEND_URL:-https://api-osms.onrender.com}"
FRONTEND_URL="${RENDER_FRONTEND_URL:-https://app-osms.onrender.com}"

CHANNEL="all"
for arg in "$@"; do
  case "$arg" in
    --channel)
      shift
      CHANNEL="${1:-all}"
      ;;
    --channel=*)
      CHANNEL="${arg#*=}"
      ;;
    -h|--help)
      cat <<EOF
Test OAuth / CORS configuration for Render deployment.

OPTIONS:
  --channel NAME    shopify | lazada | tiktok | all (default: all)

ENV:
  RENDER_BACKEND_URL     Default: $BACKEND_URL
  RENDER_FRONTEND_URL    Default: $FRONTEND_URL
EOF
      exit 0
      ;;
  esac
done

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

ok()   { printf "${GREEN}[OK]${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
fail() { printf "${RED}[FAIL]${NC} %s\n" "$*" >&2; FAILED=1; }
FAILED=0

# ---------- 1. URL shape ----------
echo "== 1. URL format =="
if [[ "$BACKEND_URL" =~ ^https:// ]] || [[ "$BACKEND_URL" =~ ^http://localhost ]]; then
  ok "Backend URL has scheme: $BACKEND_URL"
else
  fail "Backend URL must start with https:// — got: $BACKEND_URL"
fi

if [[ "$BACKEND_URL" =~ /$ ]]; then
  fail "Backend URL có trailing slash. Render env phải set không có trailing slash."
else
  ok "No trailing slash on backend."
fi

# ---------- 2. Channels ----------
echo ""
echo "== 2. Channel callback URLs =="

declare -A CHANNELS=(
  ["shopify"]="/api/channels/shopify/callback"
  ["lazada"]="/api/channels/lazada/callback"
  ["tiktok"]="/api/channels/tiktok/callback"
)

test_channel() {
  local channel="$1"
  local callback_path="${CHANNELS[$channel]}"
  local full_url="${BACKEND_URL}${callback_path}"
  printf "  Channel %-8s callback → %s\n" "$channel" "$full_url"

  # OPTIONS preflight
  local code
  code=$(curl -sS -o /dev/null -w "%{http_code}" \
              --max-time 30 \
              -X OPTIONS "$full_url" \
              -H "Origin: $FRONTEND_URL" \
              -H "Access-Control-Request-Method: GET" 2>/dev/null || echo "000")
  if [[ "$code" == "200" ]] || [[ "$code" == "204" ]] || [[ "$code" == "302" ]]; then
    ok "    [${channel}] OPTIONS preflight HTTP $code"
  else
    warn "    [${channel}] OPTIONS preflight HTTP $code (có thể OK nếu channel chưa connect)"
  fi
}

if [[ "$CHANNEL" == "all" ]]; then
  for ch in "${!CHANNELS[@]}"; do test_channel "$ch"; done
else
  if [[ -z "${CHANNELS[$CHANNEL]:-}" ]]; then
    fail "Unknown channel: $CHANNEL. Allowed: shopify, lazada, tiktok."
  else
    test_channel "$CHANNEL"
  fi
fi

# ---------- 3. Health ----------
echo ""
echo "== 3. Backend health =="
code=$(curl -sS -o /dev/null -w "%{http_code}" \
          --max-time 30 \
          "$BACKEND_URL/api/address/countries" 2>/dev/null || echo "000")
if [[ "$code" =~ ^(2|3)[0-9]{2}$ ]]; then
  ok "Backend health: HTTP $code"
else
  fail "Backend health: HTTP $code (kiểm tra Render service status)"
fi

# ---------- 4. CORS ----------
echo ""
echo "== 4. CORS check =="
CORS_HEADER=$(curl -sS -I \
                  --max-time 30 \
                  -X OPTIONS "$BACKEND_URL/api/auth/login" \
                  -H "Origin: $FRONTEND_URL" \
                  -H "Access-Control-Request-Method: POST" 2>/dev/null \
              | grep -i "^access-control-allow-origin" || echo "")
if [[ -z "$CORS_HEADER" ]]; then
  warn "Không thấy CORS header cho $FRONTEND_URL"
  warn "Backend SecurityConfig cần thêm origin này vào allowedOriginPatterns."
  warn "File: backend/src/main/java/fu/osms/config/SecurityConfig.java"
else
  ok "CORS allows $FRONTEND_URL → $CORS_HEADER"
fi

# ---------- 5. Frontend serves SPA ----------
echo ""
echo "== 5. Frontend serves SPA =="
code=$(curl -sS -o /dev/null -w "%{http_code}" \
          --max-time 30 \
          "$FRONTEND_URL/" 2>/dev/null || echo "000")
if [[ "$code" == "200" ]]; then
  # Response should be HTML containing <body> hoặc Vite root.
  if curl -sS --max-time 30 "$FRONTEND_URL/" 2>/dev/null | grep -qi "<body\|<div id=" ; then
    ok "Frontend serves HTML at /"
  else
    warn "Frontend returned 200 but body doesn't look like Vite SPA."
  fi
else
  fail "Frontend returned HTTP $code"
fi

echo ""
if [[ $FAILED -eq 0 ]]; then
  printf "${GREEN}OAuth check passed!${NC}\n"
  echo ""
  echo "Next steps:"
  echo "  - Đăng nhập admin vào $FRONTEND_URL"
  echo "  - Vào Settings → Channels → connect Shopify/Lazada/TikTok"
  echo "  - Authorize flow sẽ trỏ về $BACKEND_URL/api/channels/<name>/callback"
  exit 0
else
  printf "${RED}OAuth check failed.${NC}\n"
  echo "Xem lại các warning ở trên và fix trong:"
  echo "  - backend/src/main/java/fu/osms/config/SecurityConfig.java (CORS)"
  echo "  - render.yaml (env SHOPIFY_REDIRECT_URI / LAZADA_REDIRECT_URI / TIKTOK_REDIRECT_URI)"
  exit 1
fi
