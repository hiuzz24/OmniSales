#!/usr/bin/env bash
# =============================================================
# Setup integration-test database for OmniSales
# Creates database `osms_it` and seeds it from
# backend/hibernate-schema.sql so it matches the production
# schema that Hibernate validates against.
#
# Usage (PowerShell / Git Bash):
#   bash backend/src/test/resources/db-init.sh
#
# Override defaults via env vars:
#   PGPASSWORD=your_pw bash db-init.sh
# =============================================================

set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-postgres}"
PGPASSWORD="${PGPASSWORD:-123}"
DB_NAME="${DB_NAME:-osms_it}"

# Locate psql.exe on Windows; fall back to `psql` on Unix
if command -v psql.exe >/dev/null 2>&1; then
  PSQL="psql.exe"
elif command -v psql >/dev/null 2>&1; then
  PSQL="psql"
elif [ -x "/c/Program Files/PostgreSQL/18/bin/psql.exe" ]; then
  PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"
else
  echo "psql not found on PATH" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/../../../.." && pwd)"
SCHEMA_FILE="$REPO_ROOT/backend/hibernate-schema.sql"

if [ ! -f "$SCHEMA_FILE" ]; then
  echo "Schema file not found: $SCHEMA_FILE" >&2
  exit 1
fi

echo "[db-init] Dropping & recreating database '$DB_NAME' on $PGHOST:$PGPORT ..."
"$PSQL" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres \
  -c "DROP DATABASE IF EXISTS $DB_NAME;" >/dev/null
"$PSQL" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d postgres \
  -c "CREATE DATABASE $DB_NAME;"

echo "[db-init] Seeding schema from $SCHEMA_FILE ..."
"$PSQL" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$DB_NAME" \
  -f "$SCHEMA_FILE" >/dev/null

echo "[db-init] Done. Test DB '$DB_NAME' is ready."