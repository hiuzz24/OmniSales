-- ============================================================
-- Migration: Remove Purchase Order Inspection Flow
-- The INSPECTING / INSPECTED states no longer exist. Any orders
-- still sitting in those states are mapped to RECEIVING so they
-- can keep receiving goods (partial delivery).
-- Drops the now-unused inspection columns and tightens the
-- status CHECK constraint.
--
-- NOTE: purchase_order_items.actual_quantity is KEPT (not dropped)
-- so we can track how many units were actually received per line
-- and continue receiving the remaining quantity in later batches.
-- ============================================================
BEGIN;

-- 1. Move leftover INSPECTING / INSPECTED orders back to RECEIVING
UPDATE purchase_orders
SET status = 'RECEIVING'
WHERE status IN ('INSPECTING', 'INSPECTED');

-- 2. Drop old CHECK constraint and re-add one without inspection states
ALTER TABLE purchase_orders
    DROP CONSTRAINT IF EXISTS purchase_orders_status_check;

ALTER TABLE purchase_orders
    ADD CONSTRAINT purchase_orders_status_check
        CHECK (status IN (
            'DRAFT',
            'SENT_TO_SUPPLIER',
            'RECEIVING',
            'COMPLETED',
            'CANCELLED'
        ));

-- 3. Drop inspection-only columns (keep actual_quantity for partial-delivery tracking)
ALTER TABLE purchase_order_items
    DROP COLUMN IF EXISTS surplus_note;

ALTER TABLE purchase_orders
    DROP COLUMN IF EXISTS inspecting_at,
    DROP COLUMN IF EXISTS inspected_at;

COMMIT;
