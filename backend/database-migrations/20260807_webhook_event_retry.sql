-- ============================================================
-- Migration: Webhook event auto-retry support
-- Run this script against your PostgreSQL database (OSMS schema)
-- BEFORE starting the application (ddl-auto = validate)
--
-- Adds retry_count so WebhookEventRetrySweeper can re-enqueue
-- events stuck in RECEIVED/PROCESSING and retry FAILED events
-- up to app.webhook-retry.max-retries times.
-- ============================================================

ALTER TABLE webhook_events
    ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0;
