BEGIN;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE IF NOT EXISTS purchase_orders (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_code            VARCHAR(100) NOT NULL UNIQUE,
    supplier_id           UUID NOT NULL REFERENCES suppliers(id),
    warehouse_id          UUID NOT NULL REFERENCES warehouses(id),
    status                VARCHAR(30) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT','SENT_TO_SUPPLIER','RECEIVING','COMPLETED','CANCELLED')),
    order_date            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expected_receipt_date DATE NOT NULL,
    payment_method        VARCHAR(50),
    total_amount          NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (total_amount >= 0),
    notes                 TEXT,
    created_by            UUID REFERENCES users(id) ON DELETE SET NULL,
    sent_at               TIMESTAMPTZ,
    receiving_at          TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    version               BIGINT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_purchase_orders_status_sent
    ON purchase_orders(status, sent_at);

DO $$
BEGIN
    IF to_regprocedure('fn_set_updated_at()') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_purchase_orders_updated_at') THEN
        CREATE TRIGGER trg_purchase_orders_updated_at
            BEFORE UPDATE ON purchase_orders
            FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS purchase_order_items (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    purchase_order_id UUID NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    variant_id        UUID NOT NULL REFERENCES product_variants(id),
    quantity          INT NOT NULL CHECK (quantity > 0),
    unit_cost         NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
    total_cost        NUMERIC(14,2) GENERATED ALWAYS AS (quantity * unit_cost) STORED,
    CONSTRAINT uq_purchase_order_variant UNIQUE (purchase_order_id, variant_id)
);

CREATE INDEX IF NOT EXISTS idx_purchase_order_items_variant
    ON purchase_order_items(variant_id);

ALTER TABLE inventory_receipts
    ADD COLUMN IF NOT EXISTS purchase_order_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_receipt_purchase_order'
    ) THEN
        ALTER TABLE inventory_receipts
            ADD CONSTRAINT fk_inventory_receipt_purchase_order
            FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_inventory_receipt_purchase_order'
    ) THEN
        ALTER TABLE inventory_receipts
            ADD CONSTRAINT uq_inventory_receipt_purchase_order UNIQUE (purchase_order_id);
    END IF;
END $$;

ALTER TABLE purchase_orders
    ALTER COLUMN order_date TYPE TIMESTAMPTZ
    USING order_date::timestamp AT TIME ZONE 'Asia/Ho_Chi_Minh';

ALTER TABLE purchase_orders
    ALTER COLUMN order_date SET DEFAULT NOW();

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_type_check
    CHECK (
        type IN (
            'LOW_STOCK',
            'SYNC_FAILED',
            'ORDER_NEW',
            'ORDER_CANCELLED',
            'ORDER_PAID',
            'STOCK_TRANSFER',
            'STOCKTAKE',
            'SYNC',
            'INVENTORY'
        )
    ) NOT VALID;

ALTER TABLE notifications
    DROP CONSTRAINT IF EXISTS notifications_entity_type_check;

ALTER TABLE notifications
    ADD CONSTRAINT notifications_entity_type_check
    CHECK (
        entity_type IS NULL
        OR entity_type IN (
            'ORDER',
            'PRODUCT',
            'CHANNEL',
            'SYNC_LOG',
            'SYNC',
            'INVENTORY',
            'TRANSFER',
            'RECEIPT',
            'PURCHASE'
        )
    ) NOT VALID;

COMMIT;
