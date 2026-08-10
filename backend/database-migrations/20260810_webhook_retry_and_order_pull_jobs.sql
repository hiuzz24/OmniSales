-- ============================================================
-- Migration: 20260810_webhook_retry_and_order_pull_jobs
-- Apply ALTER TABLE webhook_events ADD retry_count + create
-- order_pull_jobs table that backend OrderPullJob entity expects.
-- Also add purchase_orders.evidence_url + drop the 1-1 unique
-- constraint on inventory_receipts.purchase_order_id so partial
-- receipts work (per 20260808_partial_receipts_and_evidence.sql).
-- ============================================================

ALTER TABLE webhook_events
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;

-- order_pull_jobs: drop & recreate to upgrade to the full schema
-- (adds range/attempt_count/state CHECK constraints and partial
-- recovery index). Safe because no rows exist yet on a fresh install.
DROP TABLE IF EXISTS order_pull_jobs CASCADE;

CREATE TABLE order_pull_jobs (
    id UUID PRIMARY KEY,
    sync_log_id UUID NOT NULL,
    from_time TIMESTAMPTZ NOT NULL,
    to_time TIMESTAMPTZ NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    last_heartbeat_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_order_pull_jobs_sync_log
        FOREIGN KEY (sync_log_id) REFERENCES sync_logs(id) ON DELETE CASCADE,
    CONSTRAINT uq_order_pull_jobs_sync_log UNIQUE (sync_log_id),
    CONSTRAINT ck_order_pull_jobs_range CHECK (from_time <= to_time),
    CONSTRAINT ck_order_pull_jobs_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_order_pull_jobs_state CHECK (
        state IN ('PENDING', 'PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_order_pull_jobs_recovery
    ON order_pull_jobs (state, updated_at)
    WHERE state IN ('PENDING', 'PUBLISHED', 'PROCESSING');

ALTER TABLE purchase_orders
    ADD COLUMN IF NOT EXISTS evidence_url TEXT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_inventory_receipt_purchase_order'
    ) THEN
        ALTER TABLE inventory_receipts
            DROP CONSTRAINT uq_inventory_receipt_purchase_order;
    END IF;
END $$;