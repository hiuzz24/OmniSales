-- =============================================================================
-- PostgreSQL schema init — chạy TRƯỚC khi Hibernate ddl-auto tạo tables.
--
-- Tại sao cần file này?
--   Các entity dùng @JdbcTypeCode(SqlTypes.NAMED_ENUM) yêu cầu PostgreSQL
--   enum type đã tồn tại trước khi Hibernate tạo table. ddl-auto: update
--   KHÔNG tự tạo custom enum types → app crash với "type does not exist".
--
-- Spring Boot chạy file này tự động nhờ:
--   spring.sql.init.mode: always
--   spring.sql.init.platform: postgresql
--   spring.jpa.defer-datasource-initialization: true (chạy SAU Hibernate)
--
-- Lưu ý: Hibernate chạy TRƯỚC schema.sql. Để enum type tồn tại trước
-- Hibernate ddl-auto, cần cấu hình platform-specific schema init.
--
-- continue-on-error: true để CREATE TYPE chạy idempotent (type đã tồn tại → skip).
-- =============================================================================

-- Idempotent: dùng DO block check tồn tại thay vì CREATE TYPE trực tiếp.
-- Cách an toàn nhất: chạy CREATE TYPE trong DO $$ ... $$ với exception handler.

DO $$ BEGIN
    CREATE TYPE user_status AS ENUM ('ACTIVE', 'INACTIVE', 'LOCKED');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE platform_type AS ENUM ('SHOPEE', 'TIKTOK', 'LAZADA', 'SHOPIFY', 'MANUAL');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE product_status AS ENUM ('ACTIVE', 'INACTIVE', 'DRAFT');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE order_status AS ENUM ('PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE inv_txn_type AS ENUM ('IMPORT', 'EXPORT', 'TRANSFER_OUT', 'TRANSFER_IN','ADJUSTMENT', 'ORDER_DEDUCT', 'ORDER_CANCEL', 'OUTBOUND');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE sync_status AS ENUM ('PENDING', 'SYNCED', 'FAILED', 'OUT_OF_SYNC');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE product_log_action AS ENUM ('CREATE', 'UPDATE', 'DELETE', 'SYNC', 'MAPPING');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE category_status AS ENUM ('ACTIVE', 'INACTIVE');
EXCEPTION WHEN duplicate_object THEN null;
END $$;