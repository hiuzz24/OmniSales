BEGIN;

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
            'ORDER_PICK_REQUIRED',
            'ORDER_READY_SHIP',
            'STOCK_TRANSFER',
            'STOCKTAKE',
            'SYNC',
            'INVENTORY'
        )
    );

COMMIT;
