-- ============================================================
-- Migration: Stocktake detail page fields
-- Run this script against your PostgreSQL database (OSMS schema)
-- BEFORE starting the application (ddl-auto = validate)
--
-- NOTE: stocktake_items.difference stays a generated column
-- (GENERATED ALWAYS AS (actual_quantity - system_quantity) STORED)
-- and is mapped in the entity with insertable=false/updatable=false.
-- ============================================================

-- 1. stocktake_sessions: notes + status actors/timestamps
ALTER TABLE stocktake_sessions
    ADD COLUMN IF NOT EXISTS notes           TEXT,
    ADD COLUMN IF NOT EXISTS started_by      UUID,
    ADD COLUMN IF NOT EXISTS started_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_by    UUID,
    ADD COLUMN IF NOT EXISTS completed_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by    UUID,
    ADD COLUMN IF NOT EXISTS cancelled_at    TIMESTAMPTZ;

-- 2. stocktake_items: allow unchecked rows (actual_quantity NULL)
ALTER TABLE stocktake_items
    ALTER COLUMN actual_quantity DROP NOT NULL;
