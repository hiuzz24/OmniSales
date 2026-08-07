#!/usr/bin/env bash
# =============================================================================
# scripts/render-warmup.sh
#
# Wake up Render services trước khi demo (free tier sleep sau 15 phút idle).
# Service đầu tiên hit sẽ mất 30-60 giây để spin up.
#
# Usage:
#   bash scripts/render-warmup.sh
#   bash scripts/render-warmup.sh --frontend-only
#   bash scripts/render-warmup.sh --backend-only
#
# Env overrides:
#   RENDER_BACKEND_URL   default: https://api-osms.onrender.com
#   RENDER_FRONTEND_URL  default: https://app-osms.onrender.com
#   WARMUP_TIMEOUT       default: 90 (giây, max per request)
# =============================================================================

set -Eeuo pipefail

BACKEND_URL="${RENDER_BACKEND_URL:-https://api-osms.onrender.com}"
FRONTEND_URL="${RENDER_FRONTEND_URL:-https://app-osms.onrender.com}"
TIMEOUT="${WARMUP_TIMEOUT:-90}"

DO_FRONTEND=true
DO_BACKEND=true

for arg in "$@"; do
  case "$arg" in
    --frontend-only) DO_BACKEND=false ;;
    --backend-only) DO_FRONTEND=false ;;
    -h|--help)
      cat <<EOF
Warm up Render services before demo.

OPTIONS:
  --frontend-only   Only warm up frontend
  --backend-only    Only warm up backend
  -h, --help        Show this help

ENV:
  RENDER_BACKEND_URL     Default: $BACKEND_URL
  RENDER_FRONTEND_URL    Default: $FRONTEND_URL
  WARMUP_TIMEOUT         Default: ${TIMEOUT}s
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

probe() {
  local name="$1"
  local url="$2"
  local attempt

  printf "→ Probing %s (%s)\n" "$name" "$url"
  for attempt in 1 2 3 4 5; do
    # HEAD first (nhẹ) — fallback GET nếu server trả 405.
    local code
    code=$(curl -sS -o /dev/null -w "%{http_code}" \
                  --max-time "$TIMEOUT" \
                  --connect-timeout 10 \
                  -X HEAD "$url" 2>/dev/null \
              || curl -sS -o /dev/null -w "%{http_code}" \
                  --max-time "$TIMEOUT" \
                  --connect-timeout 10 \
                  "$url" 2>/dev/null \
              || echo "000")
    if [[ "$code" =~ ^(2|3)[0-9]{2}$ ]]; then
      ok "$name responded with HTTP $code"
      return 0
    fi
    if [[ "$code" == "000" ]]; then
      warn "[$attempt/5] Connection failed — service có thể đang cold start. Đợi ${TIMEOUT}s..."
    else
      warn "[$attempt/5] HTTP $code, retrying..."
    fi
    sleep 10
  done
  printf "${RED}[FAIL]${NC} %s did not respond after 5 attempts.\n" "$name" >&2
  return 1
}

EXIT_CODE=0

if $DO_BACKEND; then
  probe "backend" "$BACKEND_URL/api/address/countries" || EXIT_CODE=1
fi

if $DO_FRONTEND; then
  probe "frontend" "$FRONTEND_URL/" || EXIT_CODE=1
fi

if [[ $EXIT_CODE -ne 0 ]]; then
  printf "\n${RED}Warm up FAILED.${NC}\n"
  echo "Kiểm tra:"
  echo "  1. Render dashboard → service status có 'Live' không?"
  echo "  2. Logs trong Render có lỗi gì?"
  echo "  3. Đợi thêm 1-2 phút rồi chạy lại."
  exit $EXIT_CODE
fi

printf "\n${GREEN}All services warm and ready!${NC}\n"
echo "Now opening login pages..."
echo "  Backend:  $BACKEND_URL/api/address/countries"
echo "  Frontend: $FRONTEND_URL/"
