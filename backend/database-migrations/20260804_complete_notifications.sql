BEGIN;

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_entity_type_check;

ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(40);
ALTER TABLE notifications ALTER COLUMN entity_type TYPE VARCHAR(20);

ALTER TABLE notifications ADD CONSTRAINT notifications_type_check CHECK (type IN (
    'LOW_STOCK', 'SYNC_FAILED', 'ORDER_NEW', 'ORDER_CANCELLED', 'ORDER_PAID',
    'ORDER_PICK_REQUIRED', 'ORDER_READY_SHIP', 'ORDER_SHIPPED', 'ORDER_DELIVERED',
    'ORDER_RETURN_REQUESTED', 'ORDER_RETURN_REJECTED', 'ORDER_RETURN_COMPLETED',
    'ORDER_RETURN_ATTENTION', 'CHANNEL_DISCONNECTED', 'STOCK_TRANSFER', 'STOCKTAKE',
    'SYNC', 'INVENTORY'
));

ALTER TABLE notifications ADD CONSTRAINT notifications_entity_type_check CHECK (
    entity_type IS NULL OR entity_type IN (
        'ORDER', 'RETURN', 'PRODUCT', 'CHANNEL', 'SYNC_LOG', 'SYNC', 'INVENTORY',
        'TRANSFER', 'RECEIPT', 'PURCHASE'
    )
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_entity
    ON notifications(user_id, type, entity_type, entity_id);

COMMIT;
