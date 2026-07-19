# Scripts — Test data maintenance

These scripts live under `test/scripts/` (relative path: `OmniSales/test/scripts/`).

## Quick start

| Script | Purpose | When to run |
|--------|---------|-------------|
| `scripts/dry-run-counts.js` | Count rows matching test markers | Before deleting anything |
| `scripts/backup-db.js` | Snapshot whole DB to `backend/backups/*.sql` | Before destructive cleanup |
| `scripts/delete-test-data.js` | Delete test rows in a transaction | One-time deep clean |
| `scripts/cleanup-test-data.js` | Thin CLI wrapper around `cleanup-helpers.cleanupAllTestData()` | After a test run |

## NPM scripts

Registered in `OmniSales/test/package.json`:

```bash
npm run db:backup         # node scripts/backup-db.js
npm run db:dry-run        # node scripts/dry-run-counts.js
npm run db:delete-test    # node scripts/delete-test-data.js
npm run test:clean        # node scripts/cleanup-test-data.js
```

## Detailed usage

### `backup-db.js`

Dumps every public-schema table to a SQL file using `COPY`. No binary
`pg_dump` needed.

```bash
npm run db:backup
# Writes backend/backups/osms_pre_cleanup_YYYYMMDD_HHMMSS.sql
```

Connection params default to `localhost:5432 / postgres / OSMS / password=123`.
Override with `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `DB_NAME`.

### `dry-run-counts.js`

Pure read-only — never modifies data. Prints a table:

```
marker                             rows
------------------------------------------------------------
product.name                       328
product.sku                        376
...
TOTAL                              2442
```

If total > 0, run `scripts/delete-test-data.js` next.

### `delete-test-data.js`

Wraps a set of carefully-ordered `DELETE` statements in one transaction.

```bash
# Print SQL without running
node scripts/delete-test-data.js --dry-run

# Run inside BEGIN ... ROLLBACK to validate FK ordering
node scripts/delete-test-data.js --validate

# Run interactively (asks "DELETE", then "COMMIT" or "ROLLBACK")
node scripts/delete-test-data.js

# Skip confirmations, auto-commit
node scripts/delete-test-data.js --yes
```

**Safety net:** Always run `npm run db:backup` first. Disable the
immutability triggers (`trg_*_immutable`) inside the transaction and
re-enable them at the end so the audit ledger stays protected.

### `cleanup-test-data.js`

Lightweight wrapper used by `globalTeardown` and a manual CLI.

```bash
# API mode (always safe)
npm run test:clean

# SQL mode — REQUIRES TEST_DB_SQL_CLEANUP=true (safety guard)
TEST_DB_SQL_CLEANUP=true node scripts/cleanup-test-data.js --sql
# Or use --force-sql to bypass the env-var check (use with care)
node scripts/cleanup-test-data.js --sql --force-sql
```

## Recovery

If you delete too much, restore from a backup file:

```bash
psql -h localhost -U postgres -d OSMS -f backend/backups/osms_pre_cleanup_YYYYMMDD_HHMMSS.sql
```

(On Windows without psql, restore by re-running
`backend/src/test/resources/db-init.ps1` against the test DB first,
then load the backup file with any text-based SQL client.)

## Why not TRUNCATE?

`TRUNCATE` would also delete master data (admin, manager, seed
warehouses, countries) — and would be blocked by the immutability
triggers on `inventory_transactions`, `audit_logs`, etc. The marker-based
delete keeps production data intact and is safe on a shared dev DB
provided you review the dry-run output first.