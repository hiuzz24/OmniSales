-- Apply manually before starting the backend with the order-return feature.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS external_item_id VARCHAR(200);

CREATE UNIQUE INDEX IF NOT EXISTS uq_order_item_external
    ON order_items(order_id, external_item_id)
    WHERE external_item_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS order_returns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    channel_id UUID NOT NULL REFERENCES channels(id),
    warehouse_id UUID REFERENCES warehouses(id),
    platform platform_type NOT NULL,
    external_return_id VARCHAR(200) NOT NULL,
    platform_status VARCHAR(100),
    platform_updated_at TIMESTAMPTZ,
    last_webhook_event_id VARCHAR(200),
    status VARCHAR(40) NOT NULL,
    data_validation_state VARCHAR(20) NOT NULL,
    last_action VARCHAR(20),
    action_state VARCHAR(20) NOT NULL,
    action_request_id UUID,
    action_error TEXT,
    approved_at TIMESTAMPTZ,
    inspected_at TIMESTAMPTZ,
    refund_confirmed_at TIMESTAMPTZ,
    inventory_posted_at TIMESTAMPTZ,
    last_sync_error TEXT,
    metadata JSONB,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_order_return_external UNIQUE (channel_id, external_return_id),
    CONSTRAINT ck_order_return_status CHECK (status IN (
        'PENDING_APPROVAL', 'REJECTED', 'AWAITING_RETURN', 'RETURN_IN_TRANSIT',
        'INSPECTED', 'PLATFORM_PROCESSING', 'PENDING_STOCK', 'COMPLETED', 'FAILED'
    )),
    CONSTRAINT ck_order_return_validation CHECK (data_validation_state IN ('VALID', 'INVALID')),
    CONSTRAINT ck_order_return_action_state CHECK (action_state IN ('IDLE', 'PROCESSING', 'UNKNOWN', 'FAILED')),
    CONSTRAINT ck_order_return_action CHECK (last_action IS NULL OR last_action IN ('APPROVE', 'REJECT', 'PROCESS'))
);

CREATE TABLE IF NOT EXISTS order_return_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id UUID NOT NULL REFERENCES order_returns(id) ON DELETE CASCADE,
    order_item_id UUID REFERENCES order_items(id) ON DELETE SET NULL,
    variant_id UUID REFERENCES product_variants(id),
    external_order_item_id VARCHAR(200),
    external_return_item_id VARCHAR(200),
    external_identity_key VARCHAR(420) NOT NULL,
    requested_quantity INTEGER NOT NULL,
    approved_quantity INTEGER NOT NULL,
    received_quantity INTEGER,
    restockable_quantity INTEGER,
    damaged_quantity INTEGER,
    missing_quantity INTEGER,
    refunded_quantity INTEGER,
    snapshot_sku VARCHAR(100),
    snapshot_name VARCHAR(500) NOT NULL,
    snapshot_unit_price NUMERIC(12,2),
    snapshot_cost_price NUMERIC(12,2),
    CONSTRAINT uq_return_item_identity UNIQUE (return_id, external_identity_key),
    CONSTRAINT ck_return_item_quantities_nonnegative CHECK (
        requested_quantity >= 0 AND approved_quantity >= 0
        AND (received_quantity IS NULL OR received_quantity >= 0)
        AND (restockable_quantity IS NULL OR restockable_quantity >= 0)
        AND (damaged_quantity IS NULL OR damaged_quantity >= 0)
        AND (missing_quantity IS NULL OR missing_quantity >= 0)
        AND (refunded_quantity IS NULL OR refunded_quantity >= 0)
    ),
    CONSTRAINT ck_return_item_inspection CHECK (
        (received_quantity IS NULL AND restockable_quantity IS NULL
            AND damaged_quantity IS NULL AND missing_quantity IS NULL)
        OR
        (received_quantity = restockable_quantity + damaged_quantity
            AND received_quantity + missing_quantity = approved_quantity)
    )
);

CREATE INDEX IF NOT EXISTS idx_order_returns_order ON order_returns(order_id);
CREATE INDEX IF NOT EXISTS idx_order_returns_status ON order_returns(status);
CREATE INDEX IF NOT EXISTS idx_order_return_items_order_item ON order_return_items(order_item_id);

