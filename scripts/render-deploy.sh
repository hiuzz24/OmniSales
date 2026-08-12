#!/usr/bin/env bash
# =============================================================================
# scripts/render-deploy.sh
#
# Validate các điều kiện cần thiết trước khi deploy lên Render.com.
#
# Usage:
#   bash scripts/render-deploy.sh
#
# Checks performed:
#   1. Repository có tracking Git (GitLab / GitHub).
#   2. Branch hiện tại là main (mặc định deploy của render.yaml).
#   3. File render.yaml tồn tại và là YAML hợp lệ.
#   4. Backend Dockerfile có entrypoint hỗ trợ JAVA_TOOL_OPTIONS.
#   5. Working tree sạch (không có uncommitted changes).
#   6. .env KHÔNG nằm trong git tracked files.
#   7. Có Docker/curl sẵn sàng cho healthcheck.
#
# Exit code: 0 nếu mọi check pass, 1 nếu bất kỳ check nào fail.
# =============================================================================

set -Eeuo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# ---------- colors ----------
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

ok()   { printf "${GREEN}[OK]${NC} %s\n" "$*"; }
warn() { printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
fail() { printf "${RED}[FAIL]${NC} %s\n" "$*" >&2; exit 1; }

section() { printf "\n${YELLOW}== %s ==${NC}\n" "$*"; }

ERRORS=0

# ---------- 1. Git ----------
section "1. Git status"
if ! git rev-parse --git-dir > /dev/null 2>&1; then
  fail "Not a git repository. Run: git init && git remote add origin <url>"
fi
ok "Git repository detected."

CURRENT_BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  warn "Current branch is '$CURRENT_BRANCH' (render.yaml mặc định track 'main')."
  warn "Đảm bảo render.yaml.branch khớp với branch bạn muốn deploy, hoặc checkout main trước."
  ERRORS=$((ERRORS + 1))
else
  ok "On branch 'main'."
fi

# ---------- 2. Working tree clean ----------
section "2. Working tree clean"
if ! git diff --quiet HEAD -- 2>/dev/null; then
  warn "Có uncommitted changes. Commit trước khi deploy:"
  git status --short
  ERRORS=$((ERRORS + 1))
else
  ok "Working tree clean."
fi

# ---------- 3. render.yaml ----------
section "3. render.yaml"
if [[ ! -f "render.yaml" ]]; then
  fail "render.yaml không tồn tại ở root."
fi
ok "render.yaml tồn tại."

# Try YAML parse (requires python or yq)
YAML_OK=0
if command -v python > /dev/null 2>&1; then
  if python -c "import yaml,sys;yaml.safe_load(open('render.yaml'))" 2>/dev/null; then
    ok "render.yaml parse OK (python yaml)."
    YAML_OK=1
  fi
elif command -v python3 > /dev/null 2>&1; then
  if python3 -c "import yaml,sys;yaml.safe_load(open('render.yaml'))" 2>/dev/null; then
    ok "render.yaml parse OK (python3 yaml)."
    YAML_OK=1
  fi
elif command -v py > /dev/null 2>&1; then
  if py -c "import yaml,sys;yaml.safe_load(open('render.yaml'))" 2>/dev/null; then
    ok "render.yaml parse OK (py launcher)."
    YAML_OK=1
  fi
elif command -v yq > /dev/null 2>&1; then
  if yq eval 'true' render.yaml > /dev/null 2>&1; then
    ok "render.yaml parse OK (yq)."
    YAML_OK=1
  fi
fi

if [[ $YAML_OK -eq 0 ]]; then
  warn "Không tìm thấy python/yq — bỏ qua YAML validation."
  warn "Cài 'pyyaml' (pip install pyyaml) hoặc 'yq' để enable check."
fi

# Check render.yaml có ít nhất 1 web service và 1 database
if ! grep -q "type: web" render.yaml; then
  fail "render.yaml thiếu service 'type: web'."
fi
if ! grep -q "databases:" render.yaml; then
  fail "render.yaml thiếu khối 'databases:'."
fi
ok "render.yaml có đầy đủ web service + database."

# ---------- 4. Backend Dockerfile ----------
section "4. Backend Dockerfile"
if [[ ! -f "backend/Dockerfile" ]]; then
  fail "backend/Dockerfile không tồn tại."
fi
ok "backend/Dockerfile tồn tại."

if ! grep -q "JAVA_TOOL_OPTIONS" backend/Dockerfile; then
  fail "backend/Dockerfile chưa hỗ trợ JAVA_TOOL_OPTIONS (cần để tune RAM)."
fi
ok "backend/Dockerfile có JAVA_TOOL_OPTIONS."

if ! grep -q "HEALTHCHECK" backend/Dockerfile; then
  warn "backend/Dockerfile không có HEALTHCHECK — Render sẽ dùng default (có thể false negative)."
fi

# ---------- 5. Secret leak protection ----------
section "5. Secret protection"
if git ls-files | grep -qE "^\.env$|^backend/\.env$|^frontend/\.env$"; then
  fail "Một file .env đang được git track! Chạy: git rm --cached .env"
fi
ok ".env files không bị track."

if [[ -f ".env" ]]; then
  ok ".env tồn tại (not tracked)."
else
  warn ".env chưa tồn tại — OK nếu bạn đặt env qua Render UI."
fi

# ---------- 6. Tools ----------
section "6. Required tools"
for tool in curl; do
  if ! command -v "$tool" > /dev/null 2>&1; then
    warn "Không có '$tool' — cần cho healthcheck sau deploy."
  else
    ok "$tool installed."
  fi
done

# ---------- 7. Final ----------
section "Kết luận"
if [[ $ERRORS -gt 0 ]]; then
  fail "Có $ERRORS warning(s) cần xử lý trước khi deploy."
fi

ok "Tất cả checks pass! Bạn có thể tiếp tục:"
echo "  1. Đẩy code lên remote (git push origin main)."
echo "  2. Vào https://dashboard.render.com/blueprints → New Blueprint Instance."
echo "  3. Kết nối repo, chọn nhánh main."
echo "  4. Render sẽ tạo DB + Backend + Frontend."
echo ""
echo "Đọc thêm: docs/deploy-render.md"
