-- Reset business data and keep account/auth data.
-- Keeps data in: users, roles, user_roles, refresh_tokens, password_reset_tokens, user_invite_tokens.
-- Run against PostgreSQL database OSMS when you need a clean business dataset but still want existing logins.

BEGIN;

-- Users may reference warehouses. Clear that optional link before deleting warehouses.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'users'
          AND column_name = 'warehouse_id'
    ) THEN
        UPDATE users SET warehouse_id = NULL WHERE warehouse_id IS NOT NULL;
    END IF;
END $$;

-- inventory_transactions is append-only in normal runtime, so disable its immutable user trigger for this reset.
DO $$
BEGIN
    IF to_regclass('public.inventory_transactions') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM pg_trigger
           WHERE tgrelid = 'public.inventory_transactions'::regclass
             AND tgname = 'trg_inventory_transactions_immutable'
       ) THEN
        ALTER TABLE inventory_transactions DISABLE TRIGGER trg_inventory_transactions_immutable;
    END IF;
END $$;

DO $$
DECLARE
    table_name text;
    tables_to_clear text[] := ARRAY[
        -- Sync, webhook, logs, notifications, reports
        'sync_tasks',
        'sync_logs',
        'webhook_events',
        'channel_connection_logs',
        'notifications',
        'audit_logs',
        'system_logs',
        'report_results',
        'report_configs',
        'daily_sales_summary',
        'api_metrics_daily',
        'api_endpoint_limits',
        'system_settings',
        'backup_files',

        -- Inventory documents and stock
        'stocktake_items',
        'stocktake_sessions',
        'stock_transfer_items',
        'stock_transfers',
        'inventory_issue_items',
        'inventory_issues',
        'inventory_receipt_items',
        'inventory_receipts',
        'inventory_transactions',
        'inventory_items',

        -- Orders and customers
        'order_items',
        'orders',
        'customer_platform_ids',
        'customers',

        -- Channels and catalog
        'channel_product_variants',
        'channel_products',
        'channel_credentials',
        'channels',
        'product_images',
        'product_logs',
        'product_variants',
        'products',
        'categories',

        -- Business master data
        'suppliers',
        'warehouses',
        'administrative_divisions',
        'countries'
    ];
BEGIN
    FOREACH table_name IN ARRAY tables_to_clear LOOP
        IF to_regclass('public.' || table_name) IS NOT NULL THEN
            EXECUTE format('DELETE FROM %I', table_name);
        END IF;
    END LOOP;
END $$;

DO $$
BEGIN
    IF to_regclass('public.inventory_transactions') IS NOT NULL
       AND EXISTS (
           SELECT 1
           FROM pg_trigger
           WHERE tgrelid = 'public.inventory_transactions'::regclass
             AND tgname = 'trg_inventory_transactions_immutable'
       ) THEN
        ALTER TABLE inventory_transactions ENABLE TRIGGER trg_inventory_transactions_immutable;
    END IF;
END $$;

-- Reset identity sequences for tables that use GENERATED AS IDENTITY.
DO $$
DECLARE
    seq record;
BEGIN
    FOR seq IN
        SELECT sequence_schema, sequence_name
        FROM information_schema.sequences
        WHERE sequence_schema = 'public'
    LOOP
        EXECUTE format('ALTER SEQUENCE %I.%I RESTART WITH 1', seq.sequence_schema, seq.sequence_name);
    END LOOP;
END $$;

COMMIT;
