# =============================================================
# Full IT runner for OmniSales on Windows
# Steps:
#   1. Drop, recreate and seed the `osms_it` PostgreSQL DB
#   2. Run all `*IT.java` via Failsafe
#   3. Summarise pass/fail counts
#
# Usage (PowerShell):
#   powershell -ExecutionPolicy Bypass -File backend/src/test/resources/run-it.ps1
#
# Optional env vars:
#   SKIP_DB_RESET=1   - skip step 1 (assume DB already seeded)
#   MAVEN_FLAGS=...    - extra mvn args (e.g. "-Dtest=...")
# =============================================================

$ErrorActionPreference = 'Stop'

$PS = $PSScriptRoot
$dbInit = Join-Path $PS 'db-init.ps1'
$backend = Resolve-Path (Join-Path $PS '..\..')

if (-not (Test-Path $dbInit)) {
    Write-Error "db-init.ps1 not found at $dbInit"
    exit 1
}

if (-not $env:SKIP_DB_RESET) {
    Write-Host "[run-it] (1/2) Recreating osms_it ..."
    & $dbInit
    if ($LASTEXITCODE -ne 0) {
        Write-Error "db-init.ps1 failed with exit $LASTEXITCODE"
        exit $LASTEXITCODE
    }
} else {
    Write-Host "[run-it] SKIP_DB_RESET=1 - skipping DB reset"
}

Write-Host "[run-it] (2/2) Running Failsafe ..."
Push-Location $backend
try {
    $mvnArgs = @('verify')
    if ($env:MAVEN_FLAGS) { $mvnArgs += $env:MAVEN_FLAGS -split ' ' }

    $cmd = '.\mvnw.cmd ' + ($mvnArgs -join ' ')
    Write-Host "[run-it] $cmd"
    & .\mvnw.cmd @mvnArgs
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

$reports = Join-Path $backend 'target\failsafe-reports'
if (Test-Path $reports) {
    $total = 0; $failed = 0; $errored = 0; $skipped = 0
    Get-ChildItem $reports -Filter 'TEST-*.xml' | ForEach-Object {
        [xml]$x = Get-Content $_.FullName
        $total += [int]$x.testsuite.tests
        $failed += [int]$x.testsuite.failures
        $errored += [int]$x.testsuite.errors
        $skipped += [int]$x.testsuite.skipped
    }
    Write-Host ""
    Write-Host "=================================================="
    Write-Host "IT summary: total=$total failed=$failed errored=$errored skipped=$skipped"
    Write-Host "Reports: $reports"
    Write-Host "=================================================="
}

exit $exitCode
