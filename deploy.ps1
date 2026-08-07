# =============================================================================
# deploy.ps1 — Commit, auto-pin image SHA vào render.yaml, push lên main.
#
# Cách dùng:
#   1. Stage changes bình thường: git add <files>
#   2. Chạy: .\deploy.ps1 -Message "fix: ..."
#   3. Script sẽ:
#      - Commit staged files
#      - Lấy short SHA của commit mới
#      - Đổi tag image trong render.yaml → :<SHA>
#      - Amend commit (thêm render.yaml vào)
#      - Push lên origin/main
#      - Pipeline chạy, build image, push image:<SHA> lên registry
#      - Render pull image mới (đã đổi tag → không cache)
#
# Lưu ý:
#   - Phải ở branch main (hoặc merge vào main sau)
#   - Cần quyền push lên origin/main
# =============================================================================

param(
    [Parameter(Mandatory=$true)]
    [string]$Message
)

$ErrorActionPreference = "Stop"

# --- Sanity check ---
$branch = git rev-parse --abbrev-ref HEAD
if ($branch -ne "main") {
    Write-Error "Phải ở branch main. Hiện tại: $branch"
    exit 1
}

$renderYaml = "render.yaml"
if (-not (Test-Path $renderYaml)) {
    Write-Error "Không tìm thấy render.yaml ở thư mục hiện tại."
    exit 1
}

# --- Step 1: Commit staged files ---
git commit -m $Message
if ($LASTEXITCODE -ne 0) {
    Write-Error "Commit fail."
    exit 1
}

# --- Step 2: Get full SHA (GitLab image tag dùng full SHA, không phải short) ---
$sha = git rev-parse HEAD
Write-Host "New commit SHA: $sha" -ForegroundColor Cyan

# --- Step 3: Update image tag in render.yaml ---
$content = Get-Content $renderYaml -Raw
$pattern = '(registry\.gitlab\.com/[^\s:]+/backend):[a-f0-9]+'
$newContent = [regex]::Replace($content, $pattern, "`$1:$sha")

if ($newContent -eq $content) {
    Write-Warning "Không tìm thấy image URL pattern trong render.yaml. Bỏ qua update."
} else {
    Set-Content -Path $renderYaml -Value $newContent -NoNewline
    Write-Host "Updated render.yaml → :$sha" -ForegroundColor Green
}

# --- Step 4: Amend commit to include render.yaml change ---
git add $renderYaml
git commit --amend --no-edit
if ($LASTEXITCODE -ne 0) {
    Write-Error "Amend fail."
    exit 1
}

$finalSha = git rev-parse HEAD
Write-Host "Final SHA (after amend): $finalSha" -ForegroundColor Cyan

# --- Step 5: Push ---
git push origin main
if ($LASTEXITCODE -ne 0) {
    Write-Error "Push fail."
    exit 1
}

Write-Host ""
Write-Host "=== DONE ===" -ForegroundColor Green
Write-Host "Image will be pushed as: registry.gitlab.com/.../backend:$finalSha"
Write-Host "Pipeline URL: https://gitlab.com/summer20241/26summer/ep490-g63/OmniSales/-/pipelines"
Write-Host ""
Write-Host "Sau khi pipeline xong, vào Render Dashboard → Manual Deploy."