-- ============================================================
-- Migration: Purchase Order Inspection Flow
-- status column is VARCHAR(30) with a CHECK constraint — just update it.
-- Run this script against your PostgreSQL database (OSMS schema)
-- ============================================================

-- 1. Drop the old CHECK constraint and replace with one that includes
--    INSPECTING and INSPECTED
ALTER TABLE purchase_orders
    DROP CONSTRAINT IF EXISTS purchase_orders_status_check;

ALTER TABLE purchase_orders
    ADD CONSTRAINT purchase_orders_status_check
        CHECK (status IN (
            'DRAFT',
            'SENT_TO_SUPPLIER',
            'RECEIVING',
            'INSPECTING',
            'INSPECTED',
            'COMPLETED',
            'CANCELLED'
        ));

-- 2. Add actual_quantity and surplus_note to purchase_order_items
ALTER TABLE purchase_order_items
    ADD COLUMN IF NOT EXISTS actual_quantity INTEGER DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS surplus_note    TEXT    DEFAULT NULL;

-- 3. Add inspection timestamps to purchase_orders
ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS inspecting_at TIMESTAMPTZ DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS inspected_at  TIMESTAMPTZ DEFAULT NULL;

-- Verify: check current constraint
-- SELECT conname, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid = 'purchase_orders'::regclass AND contype = 'c';
