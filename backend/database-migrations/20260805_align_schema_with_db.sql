-- ============================================================
-- Migration: align runtime DB with refreshed hibernate-schema.sql
-- Generated: 2026-08-05
-- Purpose:
--   * Add columns that the schema file declares but the live DB lacks
--   * Add CHECK constraint addition (ISSUE_GIFT) on inventory_transactions.reference_type
--   * Idempotent: safe to re-run
-- Run with: psql ... -f 20260805_align_schema_with_db.sql
-- ============================================================

BEGIN;

-- ── 1. Missing columns on existing tables ──────────────────────
ALTER TABLE order_items       ADD COLUMN IF NOT EXISTS external_item_id        VARCHAR(200);
ALTER TABLE inventory_issues  ADD COLUMN IF NOT EXISTS document_reference_id   VARCHAR(255);
ALTER TABLE suppliers         ADD COLUMN IF NOT EXISTS tax_code                VARCHAR(50);

-- ── 2. Ensure 'ISSUE_GIFT' is accepted by the reference_type CHECK on
--       inventory_transactions. Replace the existing check if needed.
ALTER TABLE inventory_transactions
    DROP CONSTRAINT IF EXISTS inventory_transactions_reference_type_check;

ALTER TABLE inventory_transactions
    ADD CONSTRAINT inventory_transactions_reference_type_check
        CHECK (reference_type IS NULL
               OR reference_type IN ('ORDER','RECEIPT','ISSUE','ISSUE_GIFT','ADJUSTMENT','TRANSFER'));

-- ── 3. (No-op safety) Make sure inv_txn_type keeps the values
--       that Java code expects: TRANSFER_OUT, TRANSFER_IN.
--       ALTER TYPE ... ADD VALUE cannot run inside a transaction in older PG,
--       so guard with an existence check first.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum e
        JOIN pg_type t ON e.enumtypid = t.oid
        WHERE t.typname = 'inv_txn_type' AND e.enumlabel = 'TRANSFER_OUT'
    ) THEN
        ALTER TYPE inv_txn_type ADD VALUE 'TRANSFER_OUT';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_enum e
        JOIN pg_type t ON e.enumtypid = t.oid
        WHERE t.typname = 'inv_txn_type' AND e.enumlabel = 'TRANSFER_IN'
    ) THEN
        ALTER TYPE inv_txn_type ADD VALUE 'TRANSFER_IN';
    END IF;
END $$;

COMMIT;

-- ============================================================
--  END
-- ============================================================
