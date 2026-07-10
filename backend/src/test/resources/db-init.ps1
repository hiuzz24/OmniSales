# =============================================================
# Setup integration-test database for OmniSales on Windows
# Creates database `osms_it` and seeds it from
# backend/hibernate-schema.sql so it matches the production
# schema that Hibernate validates against.
#
# Usage (PowerShell):
#   powershell -ExecutionPolicy Bypass -File backend/src/test/resources/db-init.ps1
#
# Env vars (all optional):
#   PGHOST, PGPORT, PGUSER, PGPASSWORD, DB_NAME
# Default PGHOST=localhost PGPORT=5432 PGUSER=postgres
# PGPASSWORD read from backend/.env if not set
# DB_NAME=osms_it
# =============================================================

$ErrorActionPreference = 'Stop'

# Locate psql.exe
$psql = $null
$candidates = @(
    "$env:ProgramFiles\PostgreSQL\18\bin\psql.exe",
    "$env:ProgramFiles\PostgreSQL\17\bin\psql.exe",
    "$env:ProgramFiles\PostgreSQL\16\bin\psql.exe",
    "$env:ProgramFiles\PostgreSQL\15\bin\psql.exe",
    "$env:ProgramFiles\PostgreSQL\14\bin\psql.exe"
)
foreach ($c in $candidates) {
    if ($c -and (Test-Path $c)) { $psql = $c; break }
}
if (-not $psql) {
    $cmd = Get-Command psql -ErrorAction SilentlyContinue
    if ($cmd) { $psql = $cmd.Source }
}
if (-not $psql) {
    Write-Error "psql not found. Looked in: $($candidates -join ', ')"
    exit 1
}
Write-Host "[db-init] Using psql: $psql"

$PGHOST    = if ($env:PGHOST)    { $env:PGHOST }    else { 'localhost' }
$PGPORT    = if ($env:PGPORT)    { $env:PGPORT }    else { '5432' }
$PGUSER    = if ($env:PGUSER)    { $env:PGUSER }    else { 'postgres' }
$DB_NAME   = if ($env:DB_NAME)   { $env:DB_NAME }   else { 'osms_it' }

# Resolve repo root regardless of where the script lives.
# Walk up from $PSScriptRoot until we find backend/hibernate-schema.sql.
$repoRoot = $null
$cur = (Resolve-Path $PSScriptRoot).Path
while ($cur -ne [System.IO.Path]::GetPathRoot($cur)) {
    $probe = Join-Path $cur 'backend\hibernate-schema.sql'
    if (Test-Path $probe) { $repoRoot = $cur; break }
    $cur = Split-Path -Parent $cur
}
if (-not $repoRoot) {
    Write-Error "Could not locate repo root (no backend\hibernate-schema.sql found above $PSScriptRoot)"
    exit 1
}

# Read PGPASSWORD from .env if not set
if (-not $env:PGPASSWORD) {
    $envFile = Join-Path $repoRoot 'backend\.env'
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*DB_PASSWORD\s*=\s*(.+)\s*$') {
                $env:PGPASSWORD = $matches[1]
            }
        }
    }
}
if (-not $env:PGPASSWORD) {
    Write-Error "PGPASSWORD not set and DB_PASSWORD not found in backend/.env"
    exit 1
}
Write-Host "[db-init] Connecting to $PGHOST`:$PGPORT as $PGUSER, db=$DB_NAME"

$schemaFile = Join-Path $repoRoot 'backend\hibernate-schema.sql'
if (-not (Test-Path $schemaFile)) {
    Write-Error "Schema file not found: $schemaFile"
    exit 1
}

Write-Host "[db-init] Dropping & recreating database '$DB_NAME' ..."
& $psql -h $PGHOST -p $PGPORT -U $PGUSER -d postgres -c "DROP DATABASE IF EXISTS $DB_NAME;" | Out-Null
& $psql -h $PGHOST -p $PGPORT -U $PGUSER -d postgres -c "CREATE DATABASE $DB_NAME;" | Out-Null

Write-Host "[db-init] Seeding schema from $schemaFile ..."
& $psql -h $PGHOST -p $PGPORT -U $PGUSER -d $DB_NAME -f $schemaFile | Out-Null

Write-Host "[db-init] Done. Test DB '$DB_NAME' is ready."
