BEGIN;

-- Allow one purchase order to receive goods in multiple batches.
-- Drop the OneToOne unique constraint so many inventory_receipts can
-- reference the same purchase order.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_inventory_receipt_purchase_order'
    ) THEN
        ALTER TABLE inventory_receipts
            DROP CONSTRAINT uq_inventory_receipt_purchase_order;
    END IF;
END $$;

-- Purchase order evidence: a single optional image (e.g. supplier
-- delivery note photo). Uploading a new one replaces the previous.
ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS evidence_url TEXT;

COMMIT;
