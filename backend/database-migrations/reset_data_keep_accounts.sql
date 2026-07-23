-- Reset business data and keep account/auth data.
-- Keeps data in: users, roles, user_roles, refresh_tokens, password_reset_tokens, user_invite_tokens.
-- Run against PostgreSQL database OSMS when you need a clean business dataset but still want existing logins.
-- This is a standalone administrative script. Clear any transaction left aborted by an earlier run.
ROLLBACK;

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

-- Immutable triggers protect runtime data, but must not block this administrative reset.
-- Remember only the triggers that are currently enabled so their original state can be restored.
CREATE TEMP TABLE reset_triggers_to_restore (
    table_name text NOT NULL,
    trigger_name text NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
    trigger_to_disable record;
BEGIN
    FOR trigger_to_disable IN
        SELECT c.relname AS table_name, t.tgname AS trigger_name
        FROM pg_trigger t
        JOIN pg_class c ON c.oid = t.tgrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public'
          AND t.tgenabled <> 'D'
          AND (c.relname, t.tgname) IN (
              ('inventory_receipts', 'trg_receipt_immutable'),
              ('inventory_issues', 'trg_issue_immutable'),
              ('inventory_transactions', 'trg_inventory_transactions_immutable'),
              ('audit_logs', 'trg_audit_logs_immutable')
          )
    LOOP
        INSERT INTO reset_triggers_to_restore (table_name, trigger_name)
        VALUES (trigger_to_disable.table_name, trigger_to_disable.trigger_name);

        EXECUTE format(
            'ALTER TABLE public.%I DISABLE TRIGGER %I',
            trigger_to_disable.table_name,
            trigger_to_disable.trigger_name
        );
    END LOOP;
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
        'purchase_order_items',
        'purchase_orders',
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
            EXECUTE format('DELETE FROM public.%I', table_name);
        END IF;
    END LOOP;
END $$;

DO $$
DECLARE
    trigger_to_restore record;
BEGIN
    FOR trigger_to_restore IN
        SELECT table_name, trigger_name
        FROM reset_triggers_to_restore
    LOOP
        EXECUTE format(
            'ALTER TABLE public.%I ENABLE TRIGGER %I',
            trigger_to_restore.table_name,
            trigger_to_restore.trigger_name
        );
    END LOOP;
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
        IF seq.sequence_name !~ '^pg_toast' THEN
            EXECUTE format('ALTER SEQUENCE %I.%I RESTART WITH 1', seq.sequence_schema, seq.sequence_name);
        END IF;
    END LOOP;
END $$;

COMMIT;
