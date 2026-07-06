-- ============================================================
--  OSMS — Full Schema Reset (DROP → CREATE → SEED)
--  Generated: 2026-06-21
--  Order: DROP children first, then parents
-- ============================================================

-- ─── 0. EXTENSIONS ──────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ─── 1. DROP TRIGGERS ───────────────────────────────────────
DO $$
    DECLARE tbl TEXT;
    BEGIN
        FOR tbl IN SELECT unnest(ARRAY[
            'users','channels','channel_credentials','products',
            'product_variants','channel_products','channel_product_variants',
            'warehouses','inventory_items','daily_sales_summary','report_configs',
            'customers','suppliers','inventory_receipts',
            'inventory_issues','stock_transfers','stocktake_sessions','orders'
            ]) LOOP
                EXECUTE format('DROP TRIGGER IF EXISTS trg_%I_updated_at ON %I', tbl, tbl);
            END LOOP;
    END;
$$;

DROP TRIGGER IF EXISTS trg_receipt_immutable              ON inventory_receipts;
DROP TRIGGER IF EXISTS trg_issue_immutable                ON inventory_issues;
DROP TRIGGER IF EXISTS trg_inventory_transactions_immutable ON inventory_transactions;
DROP TRIGGER IF EXISTS trg_orders_before_update           ON orders;
DROP TRIGGER IF EXISTS trg_audit_logs_immutable           ON audit_logs;
DROP TRIGGER IF EXISTS trg_users_warehouse_role_check     ON users;

-- ─── 2. DROP FUNCTIONS ──────────────────────────────────────
DROP FUNCTION IF EXISTS fn_set_updated_at()                    CASCADE;
DROP FUNCTION IF EXISTS fn_receipt_immutable()                 CASCADE;
DROP FUNCTION IF EXISTS fn_issue_immutable()                   CASCADE;
DROP FUNCTION IF EXISTS fn_inventory_transactions_immutable()  CASCADE;
DROP FUNCTION IF EXISTS fn_orders_before_update()              CASCADE;
DROP FUNCTION IF EXISTS fn_audit_logs_immutable()              CASCADE;
DROP FUNCTION IF EXISTS fn_check_user_warehouse_role()         CASCADE;

-- ─── 3. DROP TABLES (children → parents) ────────────────────
DROP TABLE IF EXISTS report_results               CASCADE;
DROP TABLE IF EXISTS report_configs               CASCADE;
DROP TABLE IF EXISTS daily_sales_summary          CASCADE;
DROP TABLE IF EXISTS audit_logs                   CASCADE;
DROP TABLE IF EXISTS notifications                CASCADE;
DROP TABLE IF EXISTS system_logs                  CASCADE;
DROP TABLE IF EXISTS sync_tasks                   CASCADE;
DROP TABLE IF EXISTS sync_logs                    CASCADE;
DROP TABLE IF EXISTS webhook_events               CASCADE;
DROP TABLE IF EXISTS stocktake_items              CASCADE;
DROP TABLE IF EXISTS stocktake_sessions           CASCADE;
DROP TABLE IF EXISTS stock_transfer_items         CASCADE;
DROP TABLE IF EXISTS stock_transfers              CASCADE;
DROP TABLE IF EXISTS inventory_issue_items        CASCADE;
DROP TABLE IF EXISTS inventory_issues             CASCADE;
DROP TABLE IF EXISTS inventory_receipt_items      CASCADE;
DROP TABLE IF EXISTS inventory_receipts           CASCADE;
DROP TABLE IF EXISTS inventory_transactions       CASCADE;
DROP TABLE IF EXISTS inventory_items              CASCADE;
DROP TABLE IF EXISTS order_items                  CASCADE;
DROP TABLE IF EXISTS orders                       CASCADE;
DROP TABLE IF EXISTS customer_platform_ids        CASCADE;
DROP TABLE IF EXISTS customers                    CASCADE;
DROP TABLE IF EXISTS channel_product_variants     CASCADE;
DROP TABLE IF EXISTS channel_products             CASCADE;
DROP TABLE IF EXISTS channel_connection_logs      CASCADE;
DROP TABLE IF EXISTS channel_credentials          CASCADE;
DROP TABLE IF EXISTS channels                     CASCADE;
DROP TABLE IF EXISTS product_logs                 CASCADE;
DROP TABLE IF EXISTS product_images               CASCADE;
DROP TABLE IF EXISTS product_variants             CASCADE;
DROP TABLE IF EXISTS products                     CASCADE;
DROP TABLE IF EXISTS categories                   CASCADE;
DROP TABLE IF EXISTS administrative_divisions     CASCADE;
DROP TABLE IF EXISTS countries                    CASCADE;
DROP TABLE IF EXISTS warehouses                   CASCADE;
DROP TABLE IF EXISTS suppliers                    CASCADE;
DROP TABLE IF EXISTS password_reset_tokens        CASCADE;
DROP TABLE IF EXISTS user_invite_tokens           CASCADE;
DROP TABLE IF EXISTS refresh_tokens               CASCADE;
DROP TABLE IF EXISTS user_roles                   CASCADE;
DROP TABLE IF EXISTS roles                        CASCADE;
DROP TABLE IF EXISTS users                        CASCADE;

-- ─── 4. DROP ENUM TYPES ─────────────────────────────────────
DROP TYPE IF EXISTS user_status        CASCADE;
DROP TYPE IF EXISTS platform_type      CASCADE;
DROP TYPE IF EXISTS product_status     CASCADE;
DROP TYPE IF EXISTS category_status    CASCADE;
DROP TYPE IF EXISTS order_status       CASCADE;
DROP TYPE IF EXISTS inv_txn_type       CASCADE;
DROP TYPE IF EXISTS sync_status        CASCADE;
DROP TYPE IF EXISTS product_log_action CASCADE;

-- ============================================================
--  CREATE ENUM TYPES
-- ============================================================
CREATE TYPE user_status        AS ENUM ('ACTIVE', 'INACTIVE', 'LOCKED');
CREATE TYPE platform_type      AS ENUM ('SHOPEE', 'TIKTOK', 'LAZADA', 'SHOPIFY', 'MANUAL');
CREATE TYPE product_status     AS ENUM ('ACTIVE', 'INACTIVE', 'DRAFT');
CREATE TYPE category_status    AS ENUM ('ACTIVE', 'INACTIVE');
CREATE TYPE order_status       AS ENUM ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED');
CREATE TYPE inv_txn_type       AS ENUM ('IMPORT', 'EXPORT', 'TRANSFER_OUT', 'TRANSFER_IN', 'ADJUSTMENT', 'ORDER_DEDUCT', 'ORDER_CANCEL', 'OUTBOUND', 'INBOUND');
CREATE TYPE sync_status        AS ENUM ('PENDING', 'SYNCED', 'FAILED', 'OUT_OF_SYNC');
CREATE TYPE product_log_action AS ENUM ('CREATE', 'UPDATE', 'DELETE', 'SYNC', 'MAPPING');

-- ============================================================
--  CREATE TABLES
-- ============================================================

-- ── Users & Auth ────────────────────────────────────────────
CREATE TABLE users (
                       id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       email                 VARCHAR(255) NOT NULL,
                       password_hash         VARCHAR(255) NOT NULL,
                       full_name             VARCHAR(255) NOT NULL,
                       phone                 VARCHAR(20),
                       avatar_url            TEXT,
                       status                user_status  NOT NULL DEFAULT 'INACTIVE',
                       created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                       deleted_at            TIMESTAMPTZ,
                       email_verified_at     TIMESTAMPTZ,
                       verification_token    VARCHAR(255),
                       failed_login_attempts INT          DEFAULT 0,
                       locked_until          TIMESTAMPTZ,
                       password_expired      BOOLEAN
);
CREATE UNIQUE INDEX uq_users_email_active ON users(email) WHERE deleted_at IS NULL;

CREATE TABLE roles (
                       id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                       name        VARCHAR(50) NOT NULL UNIQUE,
                       description TEXT,
                       created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE user_roles (
                            id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                            user_id    UUID        NOT NULL REFERENCES users(id)  ON DELETE CASCADE,
                            role_id    UUID        NOT NULL REFERENCES roles(id)  ON DELETE CASCADE,
                            granted_by UUID        REFERENCES users(id) ON DELETE SET NULL,
                            granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                            CONSTRAINT uq_user UNIQUE (user_id)
);

CREATE TABLE refresh_tokens (
                                id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                token_hash VARCHAR(512) NOT NULL UNIQUE,
                                expires_at TIMESTAMPTZ  NOT NULL,
                                revoked_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_cleanup ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;

CREATE TABLE password_reset_tokens (
                                       id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                       user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                       token      VARCHAR(255) NOT NULL UNIQUE,
                                       expires_at TIMESTAMPTZ  NOT NULL,
                                       used_at    TIMESTAMPTZ,
                                       created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE user_invite_tokens (
                                    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                    email       VARCHAR(255) NOT NULL,
                                    role_name   VARCHAR(50)  NOT NULL,
                                    token       VARCHAR(255) NOT NULL UNIQUE,
                                    expires_at  TIMESTAMPTZ  NOT NULL,
                                    used_at     TIMESTAMPTZ,
                                    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ── Catalogue ────────────────────────────────────────────────
CREATE TABLE categories (
                            id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                            parent_id  UUID         REFERENCES categories(id),
                            name       VARCHAR(255) NOT NULL,
                            slug       VARCHAR(255) NOT NULL UNIQUE,
                            sort_order INT          NOT NULL DEFAULT 0,
                            status     category_status NOT NULL DEFAULT 'ACTIVE',
                            created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE countries (
                           id         BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                           code       VARCHAR(10)  NOT NULL,
                           name       VARCHAR(255) NOT NULL,
                           flag_emoji VARCHAR(10)
);
CREATE UNIQUE INDEX uq_countries_code ON countries(code);

CREATE TABLE administrative_divisions (
                                          id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                                          country_code VARCHAR(10)  NOT NULL,
                                          parent_code  VARCHAR(20),
                                          code         VARCHAR(20)  NOT NULL,
                                          name         VARCHAR(255) NOT NULL,
                                          level        INT          NOT NULL
);
CREATE INDEX idx_adm_country_level ON administrative_divisions(country_code, level);
CREATE INDEX idx_adm_parent        ON administrative_divisions(country_code, parent_code);

CREATE TABLE products (
                          id                  UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                          category_id         UUID           REFERENCES categories(id) ON DELETE SET NULL,
                          sku                 VARCHAR(100),
                          name                VARCHAR(500)   NOT NULL,
                          description         TEXT,
                          brand               VARCHAR(255),
                          unit                VARCHAR(50),
                          status              product_status NOT NULL DEFAULT 'DRAFT',
                          low_stock_threshold INT            NOT NULL DEFAULT 5  CHECK (low_stock_threshold >= 0),
                          weight_grams        INT                                CHECK (weight_grams IS NULL OR weight_grams >= 0),
                          attributes          JSONB          NOT NULL DEFAULT '{}',
                          version             BIGINT         NOT NULL DEFAULT 0,
                          created_by          UUID           REFERENCES users(id) ON DELETE SET NULL,
                          updated_by          UUID           REFERENCES users(id) ON DELETE SET NULL,
                          created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                          updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
                          deleted_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_product_sku_active ON products(sku) WHERE sku IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE product_variants (
                                  id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                  product_id    UUID         NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                                  sku           VARCHAR(100) NOT NULL,
                                  name          VARCHAR(255),
                                  barcode       VARCHAR(100),
                                  price         NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (price >= 0),
                                  cost_price    NUMERIC(12,2)           CHECK (cost_price IS NULL OR cost_price >= 0),
                                  is_active     BOOLEAN       NOT NULL DEFAULT TRUE,
                                  option_values JSONB         NOT NULL DEFAULT '{}',
                                  weight_grams  INT                    CHECK (weight_grams IS NULL OR weight_grams >= 0),
                                  created_by    UUID          REFERENCES users(id) ON DELETE SET NULL,
                                  updated_by    UUID          REFERENCES users(id) ON DELETE SET NULL,
                                  created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                  updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                  deleted_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_variant_sku_active     ON product_variants(sku)     WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_variant_barcode_active ON product_variants(barcode) WHERE barcode IS NOT NULL AND deleted_at IS NULL;

CREATE TABLE product_images (
                                id         UUID     PRIMARY KEY DEFAULT gen_random_uuid(),
                                product_id UUID     NOT NULL REFERENCES products(id)         ON DELETE CASCADE,
                                variant_id UUID              REFERENCES product_variants(id) ON DELETE CASCADE,
                                url        TEXT     NOT NULL,
                                sort_order SMALLINT NOT NULL DEFAULT 0,
                                is_primary BOOLEAN  NOT NULL DEFAULT FALSE,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_product_images_primary ON product_images(product_id)              WHERE is_primary = TRUE;
CREATE UNIQUE INDEX uq_variant_images_primary ON product_images(variant_id)              WHERE is_primary = TRUE AND variant_id IS NOT NULL;

CREATE TABLE product_logs (
                              id                 UUID               PRIMARY KEY DEFAULT gen_random_uuid(),
                              product_id         UUID               NOT NULL REFERENCES products(id)         ON DELETE CASCADE,
                              variant_id         UUID                        REFERENCES product_variants(id) ON DELETE SET NULL,
                              sku                VARCHAR(100)       NOT NULL,
                              action             product_log_action NOT NULL,
                              field_changes      JSONB              NOT NULL DEFAULT '{}',
                              performed_by       UUID               REFERENCES users(id) ON DELETE SET NULL,
                              performed_by_email VARCHAR(255),
                              reference_type     VARCHAR(50)        CHECK (reference_type IS NULL OR reference_type IN ('ORDER','TRANSFER','ADJUSTMENT','IMPORT')),
                              reference_id       UUID,
                              notes              TEXT,
                              performed_at       TIMESTAMPTZ        NOT NULL DEFAULT NOW(),
                              created_at         TIMESTAMPTZ        NOT NULL DEFAULT NOW()
);

-- ── Channels ─────────────────────────────────────────────────
CREATE TABLE channels (
                          id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                          platform        platform_type NOT NULL,
                          display_name    VARCHAR(100)  NOT NULL,
                          status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING','CONNECTED','DISCONNECTED','ERROR')),
                          region          VARCHAR(10)   NOT NULL DEFAULT 'VN',
                          commission_rate NUMERIC(5,2)  DEFAULT 0,
                          metadata        JSONB         NOT NULL DEFAULT '{}',
                          last_synced_at  TIMESTAMPTZ,
                          sync_enabled    BOOLEAN       NOT NULL DEFAULT TRUE,
                          created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                          updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                          deleted_at      TIMESTAMPTZ,
                          CONSTRAINT uq_channel UNIQUE (platform, display_name)
);

CREATE TABLE channel_credentials (
                                     id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                     channel_id        UUID        NOT NULL UNIQUE REFERENCES channels(id) ON DELETE CASCADE,
                                     access_token      TEXT,
                                     refresh_token     TEXT,
                                     token_expires_at  TIMESTAMPTZ,
                                     connection_state  VARCHAR(20) NOT NULL DEFAULT 'DISCONNECTED'
                                         CHECK (connection_state IN ('CONNECTED','TOKEN_EXPIRED','REVOKED','DISCONNECTED')),
                                     last_refreshed_at TIMESTAMPTZ,
                                     refresh_error     TEXT,
                                     created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                     updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE channel_connection_logs (
                                         id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                         platform      platform_type NOT NULL,
                                         channel_id    UUID          REFERENCES channels(id) ON DELETE SET NULL,
                                         channel_name  VARCHAR(100),
                                         entity_type   VARCHAR(50)   NOT NULL DEFAULT 'CHANNEL',
                                         action        VARCHAR(30)   NOT NULL
                                             CHECK (action IN ('CONNECT', 'DISCONNECT', 'RECONNECT')),
                                         status        VARCHAR(30)   NOT NULL
                                             CHECK (status IN ('SUCCESS', 'FAILED')),
                                         message       TEXT,
                                         error_message TEXT,
                                         metadata      JSONB         DEFAULT '{}',
                                         created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_channel_connection_logs_created_at ON channel_connection_logs(created_at DESC);
CREATE INDEX idx_channel_connection_logs_platform ON channel_connection_logs(platform);
CREATE INDEX idx_channel_connection_logs_channel_id ON channel_connection_logs(channel_id);
CREATE INDEX idx_channel_connection_logs_status ON channel_connection_logs(status);
CREATE INDEX idx_channel_connection_logs_action ON channel_connection_logs(action);

CREATE TABLE channel_products (
                                  id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                  channel_id          UUID        NOT NULL REFERENCES channels(id)  ON DELETE CASCADE,
                                  product_id          UUID                 REFERENCES products(id)  ON DELETE SET NULL,
                                  external_product_id VARCHAR(200),          -- nullable by design
                                  external_status     VARCHAR(50),
                                  mapping_state       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                      CHECK (mapping_state IN ('ACTIVE','ORPHANED','ARCHIVED')),
                                  sync_status         sync_status NOT NULL DEFAULT 'PENDING',
                                  last_synced_at      TIMESTAMPTZ,
                                  last_sync_error     TEXT,
                                  created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                  CONSTRAINT uq_channel_product UNIQUE (channel_id, external_product_id)
);

CREATE TABLE channel_product_variants (
                                          id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                          channel_product_id  UUID          NOT NULL REFERENCES channel_products(id)   ON DELETE CASCADE,
                                          variant_id          UUID          NOT NULL REFERENCES product_variants(id)   ON DELETE CASCADE,
                                          external_variant_id VARCHAR(200)  NOT NULL,
                                          external_sku        VARCHAR(200),
                                          external_price      NUMERIC(12,2) CHECK (external_price IS NULL OR external_price >= 0),
                                          sync_status         sync_status   NOT NULL DEFAULT 'PENDING',
                                          last_synced_at      TIMESTAMPTZ,
                                          metadata            JSONB         DEFAULT '{}',
                                          created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                          updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                          CONSTRAINT uq_cpv_external UNIQUE (channel_product_id, external_variant_id),
                                          CONSTRAINT uq_cpv_internal UNIQUE (channel_product_id, variant_id)
);

-- ── Customers ────────────────────────────────────────────────
CREATE TABLE customers (
                           id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                           full_name  VARCHAR(255),
                           code       VARCHAR(20)  NOT NULL UNIQUE,
                           gender     VARCHAR(50),
                           birth      DATE,
                           phone      VARCHAR(20),
                           email      VARCHAR(255),
                           address    JSONB        DEFAULT '{}',
                           notes      TEXT,
                           created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           is_active  BOOLEAN      NOT NULL DEFAULT TRUE
);
CREATE INDEX idx_customers_phone ON customers(phone) WHERE phone IS NOT NULL;

CREATE TABLE customer_platform_ids (
                                       id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                       customer_id          UUID          NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
                                       platform             platform_type NOT NULL,
                                       external_customer_id VARCHAR(200)  NOT NULL,
                                       created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                       CONSTRAINT uq_customer_platform UNIQUE (platform, external_customer_id)
);

-- ── Orders ───────────────────────────────────────────────────
CREATE TABLE orders (
                        id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                        channel_id        UUID          REFERENCES channels(id)   ON DELETE SET NULL,
                        customer_id       UUID          REFERENCES customers(id)  ON DELETE SET NULL,
                        platform          platform_type NOT NULL,
                        channel_name      VARCHAR(100)  NOT NULL,
                        external_order_id VARCHAR(200)  NOT NULL,
                        status            order_status  NOT NULL DEFAULT 'PENDING',
                        payment_status    VARCHAR(20)   NOT NULL DEFAULT 'UNPAID'
                            CHECK (payment_status IN ('UNPAID','PAID','REFUNDED')),
    -- NOTE: PARTIAL was removed on 2026-06-20, keeping schema for reference
                        status_changed_at TIMESTAMPTZ,
                        buyer_name        VARCHAR(255),
                        buyer_phone       VARCHAR(50),
                        shipping_address  JSONB         NOT NULL DEFAULT '{}',
                        subtotal          NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (subtotal >= 0),
                        discount_amount   NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
                        shipping_fee      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (shipping_fee >= 0),
                        total_amount      NUMERIC(12,2) GENERATED ALWAYS AS (GREATEST(0, subtotal - discount_amount + shipping_fee)) STORED,
                        currency          VARCHAR(3)    NOT NULL DEFAULT 'VND',
                        note              TEXT,
                        tracking_number   VARCHAR(200),
                        cancel_reason     VARCHAR(255),
                        cancelled_by      UUID          REFERENCES users(id) ON DELETE SET NULL,
                        version           BIGINT        NOT NULL DEFAULT 0,
                        created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                        updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_order_external_channel ON orders(channel_id, external_order_id) WHERE channel_id IS NOT NULL;
CREATE UNIQUE INDEX uq_order_external_manual  ON orders(external_order_id)             WHERE channel_id IS NULL;
CREATE INDEX        idx_orders_customer       ON orders(customer_id)                   WHERE customer_id IS NOT NULL;

CREATE TABLE order_items (
                             id                 UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                             order_id           UUID          NOT NULL REFERENCES orders(id)                  ON DELETE CASCADE,
                             variant_id         UUID                   REFERENCES product_variants(id)        ON DELETE SET NULL,
                             channel_variant_id UUID                   REFERENCES channel_product_variants(id) ON DELETE SET NULL,
                             sku                VARCHAR(100),
                             name               VARCHAR(500)  NOT NULL,
                             quantity           INT           NOT NULL CHECK (quantity > 0),
                             unit_price         NUMERIC(12,2) NOT NULL CHECK (unit_price >= 0),
                             discount_amount    NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (discount_amount >= 0),
                             total_price        NUMERIC(12,2) GENERATED ALWAYS AS (GREATEST(0, quantity * unit_price - discount_amount)) STORED,
                             cost_price         NUMERIC(12,2)
);
CREATE INDEX idx_order_items_order ON order_items(order_id);

-- ── Suppliers & Warehouses ───────────────────────────────────
CREATE TABLE suppliers (
                           id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                           name         VARCHAR(255) NOT NULL,
                           contact_name VARCHAR(255),
                           phone        VARCHAR(50),
                           email        VARCHAR(255),
                           address      TEXT,
                           supplier_code VARCHAR(100) NOT NULL UNIQUE,
                           is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
                           created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                           updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE warehouses (
                            id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                            name       VARCHAR(255) NOT NULL,
                            address    TEXT,
                            is_active  BOOLEAN      NOT NULL DEFAULT TRUE,
                            created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                            updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                            deleted_at TIMESTAMPTZ
);

ALTER TABLE users
    ADD COLUMN warehouse_id UUID REFERENCES warehouses(id) ON DELETE SET NULL;

-- ── Inventory ────────────────────────────────────────────────
CREATE TABLE inventory_items (
                                 id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                 warehouse_id        UUID          NOT NULL REFERENCES warehouses(id),
                                 variant_id          UUID          NOT NULL REFERENCES product_variants(id) ON DELETE CASCADE,
                                 quantity_on_hand    INT           NOT NULL DEFAULT 0,
                                 reserved_quantity   INT           NOT NULL DEFAULT 0,
                                 CONSTRAINT chk_inventory_reserved_lte_onhand CHECK (reserved_quantity <= quantity_on_hand),
                                 available_quantity  INT           GENERATED ALWAYS AS (quantity_on_hand - reserved_quantity) STORED,
                                 average_cost        NUMERIC(12,2) CHECK (average_cost IS NULL OR average_cost >= 0),
                                 low_stock_threshold INT           NOT NULL DEFAULT 5 CHECK (low_stock_threshold >= 0),
                                 version             BIGINT        NOT NULL DEFAULT 0,
                                 updated_by          UUID          REFERENCES users(id) ON DELETE SET NULL,
                                 updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                 CONSTRAINT uq_inventory_item UNIQUE (warehouse_id, variant_id)
);

CREATE TABLE inventory_transactions (
                                        id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                        warehouse_id    UUID         NOT NULL REFERENCES warehouses(id),
                                        variant_id      UUID         NOT NULL REFERENCES product_variants(id),
                                        type            inv_txn_type NOT NULL,
                                        reference_type  VARCHAR(15)  CHECK (reference_type IS NULL OR reference_type IN ('ORDER','RECEIPT','ISSUE','ADJUSTMENT', 'TRANSFER', 'SYNC')),
                                        reference_id    UUID,
                                        quantity_change INT          NOT NULL CHECK (quantity_change <> 0),
                                        quantity_before INT          NOT NULL,
                                        quantity_after  INT          NOT NULL,
                                        CONSTRAINT chk_inv_txn_ledger_math CHECK (quantity_after = quantity_before + quantity_change),
                                        unit_cost       NUMERIC(12,2) CHECK (unit_cost IS NULL OR unit_cost >= 0),
                                        note            TEXT,
                                        performed_by    UUID          REFERENCES users(id) ON DELETE SET NULL,
                                        performed_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_inv_txn_variant ON inventory_transactions(variant_id, performed_at DESC);

CREATE TABLE inventory_receipts (
                                    id             UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                    warehouse_id   UUID          NOT NULL REFERENCES warehouses(id),
                                    supplier_id    UUID          REFERENCES suppliers(id) ON DELETE SET NULL,
                                    receipt_code   VARCHAR(100)  NOT NULL UNIQUE,
                                    invoice_number VARCHAR(100),
                                    status         VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'
                                        CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
                                    total_cost     NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (total_cost >= 0),
                                    received_at    TIMESTAMPTZ,
                                    notes          TEXT,
                                    created_by     UUID          REFERENCES users(id) ON DELETE SET NULL,
                                    approved_by    UUID          REFERENCES users(id) ON DELETE SET NULL,
                                    confirmed_at   TIMESTAMPTZ,
                                    created_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                    updated_at     TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory_receipt_items (
                                         id              UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                         receipt_id      UUID          NOT NULL REFERENCES inventory_receipts(id) ON DELETE CASCADE,
                                         variant_id      UUID          NOT NULL REFERENCES product_variants(id),
                                         quantity        INT           NOT NULL CHECK (quantity > 0),
                                         unit_cost       NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
                                         total_cost      NUMERIC(14,2) GENERATED ALWAYS AS (quantity * unit_cost) STORED,
                                         avg_cost_before NUMERIC(12,2),
                                         avg_cost_after  NUMERIC(12,2),
                                         notes           TEXT
);

CREATE TABLE inventory_issues (
                                  id           UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                  warehouse_id UUID          NOT NULL REFERENCES warehouses(id),
                                  issue_code   VARCHAR(100)  NOT NULL UNIQUE,
                                  issue_type   VARCHAR(10)   NOT NULL CHECK (issue_type IN ('ORDER','ADJUSTMENT','DISPOSAL','TRANSFER')),
                                  status       VARCHAR(10)   NOT NULL DEFAULT 'DRAFT'
                                      CHECK (status IN ('DRAFT','CONFIRMED','CANCELLED')),
                                  reference_id UUID,
                                  document_reference_id VARCHAR(255),
                                  recipient    VARCHAR(255),
                                  total_cost   NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (total_cost >= 0),
                                  notes        TEXT,
                                  created_by   UUID          REFERENCES users(id) ON DELETE SET NULL,
                                  approved_by  UUID          REFERENCES users(id) ON DELETE SET NULL,
                                  confirmed_at TIMESTAMPTZ,
                                  created_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                  updated_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE inventory_issue_items (
                                       id         UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                       issue_id   UUID          NOT NULL REFERENCES inventory_issues(id) ON DELETE CASCADE,
                                       variant_id UUID          NOT NULL REFERENCES product_variants(id),
                                       quantity   INT           NOT NULL CHECK (quantity > 0),
                                       unit_cost  NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
                                       total_cost NUMERIC(14,2) GENERATED ALWAYS AS (quantity * unit_cost) STORED,
                                       notes      TEXT
);

CREATE TABLE stock_transfers (
                                 id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                 from_warehouse_id UUID        NOT NULL REFERENCES warehouses(id),
                                 to_warehouse_id   UUID        NOT NULL REFERENCES warehouses(id),
                                 transfer_code     VARCHAR(100) NOT NULL UNIQUE,
                                 note              TEXT,
                                 status            VARCHAR(10)  NOT NULL DEFAULT 'DRAFT'
                                     CHECK (status IN ('DRAFT','IN_TRANSIT','RECEIVED','CANCELLED')),
                                 created_by        UUID         REFERENCES users(id) ON DELETE SET NULL,
                                 approved_by       UUID         REFERENCES users(id) ON DELETE SET NULL,
                                 created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                                 updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                                 transfer_time     TIMESTAMPTZ
);

CREATE TABLE stock_transfer_items (
                                      id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                      transfer_id UUID          NOT NULL REFERENCES stock_transfers(id) ON DELETE CASCADE,
                                      variant_id  UUID          NOT NULL REFERENCES product_variants(id),
                                      quantity    INT           NOT NULL CHECK (quantity > 0),
                                      unit_cost   NUMERIC(12,2) NOT NULL CHECK (unit_cost >= 0),
                                      notes       TEXT
);

CREATE TABLE stocktake_sessions (
                                    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                    warehouse_id   UUID         NOT NULL REFERENCES warehouses(id),
                                    session_code   VARCHAR(100) NOT NULL UNIQUE,
                                    scheduled_date DATE,
                                    status         VARCHAR(15)  NOT NULL DEFAULT 'DRAFT'
                                        CHECK (status IN ('DRAFT','IN_PROGRESS','COMPLETED','CANCELLED')),
                                    created_by     UUID         REFERENCES users(id) ON DELETE SET NULL,
                                    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                                    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE stocktake_items (
                                 id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                 session_id      UUID NOT NULL REFERENCES stocktake_sessions(id) ON DELETE CASCADE,
                                 variant_id      UUID NOT NULL REFERENCES product_variants(id),
                                 system_quantity INT  NOT NULL,
                                 actual_quantity INT  NOT NULL,
                                 difference      INT  GENERATED ALWAYS AS (actual_quantity - system_quantity) STORED,
                                 notes           TEXT
);

-- ── Webhooks & Sync ──────────────────────────────────────────
CREATE TABLE webhook_events (
                                id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                channel_id        UUID          REFERENCES channels(id) ON DELETE SET NULL,
                                platform          platform_type NOT NULL,
                                event_type        VARCHAR(100)  NOT NULL,
                                external_event_id VARCHAR(200),
                                status            VARCHAR(10)   NOT NULL DEFAULT 'RECEIVED'
                                    CHECK (status IN ('RECEIVED','PROCESSING','PROCESSED','FAILED','IGNORED')),
                                raw_payload       JSONB         NOT NULL,
                                error_message     TEXT,
                                received_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
                                processed_at      TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_webhook_dedup ON webhook_events(platform, external_event_id) WHERE external_event_id IS NOT NULL;

CREATE TABLE sync_logs (
                           id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                           channel_id      UUID        REFERENCES channels(id) ON DELETE SET NULL,
                           product_id      UUID        REFERENCES products(id) ON DELETE SET NULL,
                           job_type        VARCHAR(50) NOT NULL,
                           idempotency_key VARCHAR(200) UNIQUE,
                           status          sync_status NOT NULL DEFAULT 'PENDING',
                           total_items     INT,
                           success_count   INT         NOT NULL DEFAULT 0,
                           fail_count      INT         NOT NULL DEFAULT 0,
                           error_summary   TEXT,
                           retry_of_id     UUID        REFERENCES sync_logs(id) ON DELETE SET NULL,
                           triggered_by    UUID        REFERENCES users(id) ON DELETE SET NULL,
                           started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                           completed_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_sync_logs_running ON sync_logs(channel_id, job_type) WHERE status = 'PENDING';

CREATE TABLE sync_tasks (
                            id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                            sync_log_id     UUID        NOT NULL REFERENCES sync_logs(id) ON DELETE CASCADE,
                            channel_id      UUID        NOT NULL REFERENCES channels(id)  ON DELETE RESTRICT,
                            status          VARCHAR(10) NOT NULL DEFAULT 'PENDING'
                                CHECK (status IN ('PENDING','RUNNING','SUCCESS','FAILED','TIMEOUT','SKIPPED')),
                            items_processed INT         NOT NULL DEFAULT 0,
                            items_failed    INT         NOT NULL DEFAULT 0,
                            error_message   TEXT,
                            started_at      TIMESTAMPTZ,
                            completed_at    TIMESTAMPTZ,
                            timeout_seconds INT         NOT NULL DEFAULT 30,
                            CONSTRAINT uq_sync_task_per_channel UNIQUE (sync_log_id, channel_id)
);

-- ── Logging & Reporting ──────────────────────────────────────
CREATE TABLE system_logs (
                             id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                             level          VARCHAR(5)  NOT NULL CHECK (level IN ('DEBUG','INFO','WARN','ERROR')),
                             component      VARCHAR(100) NOT NULL,
                             message        TEXT        NOT NULL,
                             context        JSONB,
                             reference_type VARCHAR(50),
                             reference_id   UUID,
                             stack_trace    TEXT,
                             logged_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE notifications (
                               id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               user_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
                               type        VARCHAR(20) NOT NULL CHECK (type IN ('LOW_STOCK','SYNC_FAILED','ORDER_NEW','ORDER_CANCELLED','STOCK_TRANSFER')),
                               title       VARCHAR(255) NOT NULL,
                               body        TEXT,
                               read_at     TIMESTAMPTZ,
                               entity_type VARCHAR(10) CHECK (entity_type IS NULL OR entity_type IN ('ORDER','PRODUCT','CHANNEL','SYNC_LOG','INVENTORY','TRANSFER')),
                               entity_id   UUID,
                               created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_unread ON notifications(user_id, read_at, created_at) WHERE read_at IS NULL;

CREATE TABLE audit_logs (
                            id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                            actor_id     UUID        REFERENCES users(id) ON DELETE SET NULL,
                            actor_email  VARCHAR(255) NOT NULL,
                            action       VARCHAR(30) NOT NULL
                                CHECK (action IN ('CREATE','UPDATE','DELETE','LOGIN','LOGOUT','EXPORT','CONNECT','DISCONNECT','STATUS_CHANGE','ORDER_CANCEL','PAYMENT_STATUS_CHANGE')),
                            entity_type  VARCHAR(10) NOT NULL
                                CHECK (entity_type IN ('PRODUCT','VARIANT','ORDER','INVENTORY','CHANNEL','WAREHOUSE','USER')),
                            entity_id    UUID,
                            entity_name  VARCHAR(500),
                            changes      JSONB,
                            performed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE daily_sales_summary (
                                     id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
                                     channel_id        UUID          REFERENCES channels(id) ON DELETE SET NULL,
                                     date              DATE          NOT NULL,
                                     order_count       INT           NOT NULL DEFAULT 0,
                                     revenue           NUMERIC(14,2) NOT NULL DEFAULT 0,
                                     units_sold        INT           NOT NULL DEFAULT 0,
                                     is_dirty          BOOLEAN       NOT NULL DEFAULT TRUE,
                                     last_refreshed_at TIMESTAMPTZ,
                                     updated_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);
CREATE UNIQUE INDEX uq_daily_summary_total       ON daily_sales_summary(date)              WHERE channel_id IS NULL;
CREATE UNIQUE INDEX uq_daily_summary_per_channel ON daily_sales_summary(channel_id, date)  WHERE channel_id IS NOT NULL;

CREATE TABLE report_configs (
                                id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                name        VARCHAR(200) NOT NULL,
                                description TEXT,
                                report_type VARCHAR(50)  NOT NULL,
                                parameters  JSONB        NOT NULL DEFAULT '{}',
                                query_def   JSONB        NOT NULL,
                                columns     JSONB        NOT NULL,
                                schedule    VARCHAR(50),
                                is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
                                created_by  UUID         REFERENCES users(id) ON DELETE SET NULL,
                                created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
                                updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE report_results (
                                id               UUID  PRIMARY KEY DEFAULT gen_random_uuid(),
                                report_config_id UUID  NOT NULL REFERENCES report_configs(id) ON DELETE CASCADE,
                                generated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                parameters_used  JSONB NOT NULL,
                                result_data      JSONB NOT NULL,
                                row_count        INT   NOT NULL,
                                execution_time_ms INT,
                                expires_at       TIMESTAMPTZ,
                                created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ============================================================
--  FUNCTIONS & TRIGGERS
-- ============================================================

CREATE OR REPLACE FUNCTION fn_set_updated_at()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

DO $$
    DECLARE tbl TEXT;
    BEGIN
        FOR tbl IN SELECT unnest(ARRAY[
            'users','channels','channel_credentials','products',
            'product_variants','channel_products','channel_product_variants',
            'warehouses','inventory_items','daily_sales_summary','report_configs',
            'customers','suppliers','inventory_receipts',
            'inventory_issues','stock_transfers','stocktake_sessions'
            ]) LOOP
                EXECUTE format(
                        'CREATE TRIGGER trg_%I_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at()',
                        tbl, tbl
                        );
            END LOOP;
    END;
$$;

-- Immutable: inventory_receipts (CONFIRMED)
CREATE OR REPLACE FUNCTION fn_receipt_immutable()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'Cannot update or delete a confirmed receipt (id: %)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_receipt_immutable
    BEFORE UPDATE OR DELETE ON inventory_receipts
    FOR EACH ROW EXECUTE FUNCTION fn_receipt_immutable();

-- Immutable: inventory_issues (CONFIRMED)
CREATE OR REPLACE FUNCTION fn_issue_immutable()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'Cannot update or delete a confirmed issue (id: %)', OLD.id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_issue_immutable
    BEFORE UPDATE OR DELETE ON inventory_issues
    FOR EACH ROW EXECUTE FUNCTION fn_issue_immutable();

-- Immutable: inventory_transactions (append-only ledger)
CREATE OR REPLACE FUNCTION fn_inventory_transactions_immutable()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'inventory_transactions is immutable — UPDATE not allowed (id: %)', OLD.id;
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'inventory_transactions is immutable — DELETE not allowed (id: %)', OLD.id;
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER trg_inventory_transactions_immutable
    BEFORE UPDATE OR DELETE ON inventory_transactions
    FOR EACH ROW EXECUTE FUNCTION fn_inventory_transactions_immutable();

-- Orders: auto updated_at + status_changed_at
CREATE OR REPLACE FUNCTION fn_orders_before_update()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = NOW();
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        NEW.status_changed_at = NOW();
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_orders_before_update
    BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION fn_orders_before_update();

-- Users: only warehouse staff can be assigned to a warehouse
CREATE OR REPLACE FUNCTION fn_check_user_warehouse_role()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    user_role_code VARCHAR(50);
BEGIN
    IF NEW.warehouse_id IS NOT NULL THEN
        SELECT r.name INTO user_role_code
        FROM user_roles ur
                 JOIN roles r ON ur.role_id = r.id
        WHERE ur.user_id = NEW.id;

        IF user_role_code IS NULL OR (user_role_code != 'OPERATIONS' AND user_role_code != 'SALES') THEN
            RAISE EXCEPTION 'Chỉ nhân viên OPERATIONS hoặc SALES mới được phép chỉ định kho làm việc.';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_users_warehouse_role_check
    BEFORE INSERT OR UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION fn_check_user_warehouse_role();

-- Immutable: audit_logs
CREATE OR REPLACE FUNCTION fn_audit_logs_immutable()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        RAISE EXCEPTION 'audit_logs is immutable — UPDATE not allowed (id: %)', OLD.id;
    ELSIF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'audit_logs is immutable — DELETE not allowed (id: %)', OLD.id;
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER trg_audit_logs_immutable
    BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION fn_audit_logs_immutable();


-- ============================================================
--  ACCOUNT MOCK DATA ONLY
--  Password for all accounts: 11111111
-- ============================================================

INSERT INTO roles (name, description) VALUES
    ('SYSTEM_ADMIN', 'System administrator'),
    ('OWNER',        'Shop owner with full access'),
    ('OPERATIONS',   'Operations staff'),
    ('SALES',        'Sales staff')
ON CONFLICT DO NOTHING;

INSERT INTO users (id, email, password_hash, full_name, phone, status, email_verified_at) VALUES
    ('b0b1c2d3-0000-0000-0000-000000000001', 'admin@osms.vn',   crypt('11111111', gen_salt('bf', 10)), 'Quản trị hệ thống', '0901000001', 'ACTIVE', NOW()),
    ('b0b1c2d3-0000-0000-0000-000000000002', 'manager@osms.vn', crypt('11111111', gen_salt('bf', 10)), 'Quản lý cửa hàng',   '0901000002', 'ACTIVE', NOW()),
    ('b0b1c2d3-0000-0000-0000-000000000003', 'staff@osms.vn',   crypt('11111111', gen_salt('bf', 10)), 'Nhân viên kho',      '0901000003', 'ACTIVE', NOW()),
    ('b0b1c2d3-0000-0000-0000-000000000004', 'sales@osms.vn',   crypt('11111111', gen_salt('bf', 10)), 'Nhân viên bán hàng', '0901000004', 'ACTIVE', NOW())
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (id, user_id, role_id) VALUES
    ('c0b1c2d3-0000-0000-0000-000000000001', 'b0b1c2d3-0000-0000-0000-000000000001', (SELECT id FROM roles WHERE name = 'SYSTEM_ADMIN')),
    ('c0b1c2d3-0000-0000-0000-000000000002', 'b0b1c2d3-0000-0000-0000-000000000002', (SELECT id FROM roles WHERE name = 'OWNER')),
    ('c0b1c2d3-0000-0000-0000-000000000003', 'b0b1c2d3-0000-0000-0000-000000000003', (SELECT id FROM roles WHERE name = 'OPERATIONS')),
    ('c0b1c2d3-0000-0000-0000-000000000004', 'b0b1c2d3-0000-0000-0000-000000000004', (SELECT id FROM roles WHERE name = 'SALES'))
ON CONFLICT DO NOTHING;

-- ============================================================
--  END OF SCRIPT
-- ============================================================
