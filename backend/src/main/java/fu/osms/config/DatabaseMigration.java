package fu.osms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigration {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Chạy migrations SAU khi Tomcat đã bind port và app ready.
     * Dùng @EventListener(ApplicationReadyEvent.class) thay vì @PostConstruct để:
     *   1. Tránh block Tomcat startup (Render port scan timeout 30s)
     *   2. Cho phép app accept traffic NGAY khi ready, migration chạy nền
     * @Async để không block main thread sau khi ready event.
     */
    @Async
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void migrate() {
        log.info("DatabaseMigration: starting migrations in background...");
        try {
            jdbcTemplate.execute("""
                        ALTER TABLE customers
                        ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true
                    """);
            log.info("Migration: added is_active column to customers table");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute(
                    """
                                ALTER TABLE audit_logs
                                DROP CONSTRAINT IF EXISTS chk_audit_action,
                                DROP CONSTRAINT IF EXISTS audit_logs_action_check,
                                ADD CONSTRAINT audit_logs_action_check
                                CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'EXPORT', 'CONNECT', 'DISCONNECT', 'STATUS_CHANGE', 'ORDER_CANCEL', 'PAYMENT_STATUS_CHANGE', 'STOCK_CONFIRMED_MANUALLY'))
                            """);
            Boolean hasStatusChange = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'audit_logs_action_check'
                              AND pg_get_constraintdef(oid) LIKE '%STATUS_CHANGE%'
                        )
                    """, Boolean.class);
            Boolean hasPaymentStatusChange = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'audit_logs_action_check'
                              AND pg_get_constraintdef(oid) LIKE '%PAYMENT_STATUS_CHANGE%'
                        )
                    """, Boolean.class);
            Boolean hasManualStockConfirmation = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'audit_logs_action_check'
                              AND pg_get_constraintdef(oid) LIKE '%STOCK_CONFIRMED_MANUALLY%'
                        )
                    """, Boolean.class);
            if (Boolean.TRUE.equals(hasStatusChange)
                    && Boolean.TRUE.equals(hasPaymentStatusChange)
                    && Boolean.TRUE.equals(hasManualStockConfirmation)) {
                log.info(
                        "Migration: audit_logs_action_check constraint updated with status and manual stock actions");
            } else {
                log.error("Migration FAILED: audit_logs_action_check is missing one or more required actions!");
            }
        } catch (Exception e) {
            log.error("Migration error updating audit_logs constraint: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE audit_logs ALTER COLUMN action TYPE VARCHAR(30)
                    """);
            log.info("Migration: widened audit_logs.action column to VARCHAR(30)");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for audit_logs.action widening: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders
                        DROP CONSTRAINT IF EXISTS orders_payment_status_check,
                        ADD CONSTRAINT orders_payment_status_check
                        CHECK (payment_status IN ('UNPAID', 'PAID', 'REFUNDED'))
                    """);
            log.info("Migration: orders payment_status constraint updated (removed PARTIAL)");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders payment_status: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE sync_logs ADD COLUMN IF NOT EXISTS product_id UUID REFERENCES products(id)
                    """);
            log.info("Migration: added product_id column to sync_logs table");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for sync_logs product_id: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE users ADD COLUMN IF NOT EXISTS password_expired BOOLEAN
                    """);
            log.info("Migration: added password_expired column to users table");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for users password_expired: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE users ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMPTZ;
                        UPDATE users
                        SET password_changed_at = COALESCE(updated_at, created_at, NOW())
                        WHERE password_changed_at IS NULL;
                    """);
            log.info("Migration: added and initialized users.password_changed_at");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for users password_changed_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS note TEXT
                    """);
            log.info("Migration: added note column to stock_transfers table");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for stock_transfers note: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE inventory_items
                        DROP CONSTRAINT IF EXISTS chk_inventory_quantities_nonnegative,
                        DROP CONSTRAINT IF EXISTS chk_inventory_reserved_lte_onhand,
                        ADD CONSTRAINT chk_inventory_reserved_lte_onhand
                        CHECK (reserved_quantity <= quantity_on_hand)
                    """);
            log.info("Migration: restored inventory_items reserved <= on-hand constraint");
        } catch (Exception e) {
            log.warn("Migration skipped or failed for inventory_items reserved constraint: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE channel_credentials
                        ADD COLUMN IF NOT EXISTS refresh_token_expires_at TIMESTAMPTZ
                    """);
            log.info("Migration: added refresh_token_expires_at column to channel_credentials table");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for channel_credentials.refresh_token_expires_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE notifications
                        DROP CONSTRAINT IF EXISTS notifications_type_check,
                        DROP CONSTRAINT IF EXISTS notifications_entity_type_check;
                        ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(40);
                        ALTER TABLE notifications ALTER COLUMN entity_type TYPE VARCHAR(20);
                        ALTER TABLE notifications
                        ADD CONSTRAINT notifications_type_check
                        CHECK (type IN (
                            'LOW_STOCK',
                            'SYNC_FAILED',
                            'ORDER_NEW',
                            'ORDER_CANCELLED',
                            'ORDER_PAID',
                            'ORDER_PICK_REQUIRED',
                            'ORDER_READY_SHIP',
                            'ORDER_SHIPPED',
                            'ORDER_DELIVERED',
                            'ORDER_RETURN_REQUESTED',
                            'ORDER_RETURN_REJECTED',
       'ORDER_RETURN_COMPLETED',
       'ORDER_RETURN_ATTENTION',
       'ORDER_WAITING_STOCK',
       'ORDER_WAITING_STOCK_EXPIRED',
                            'ORDER_PLATFORM_STOCK_CONFLICT',
                            'ORDER_BUYER_CANCEL_REQUESTED',
                            'CHANNEL_DISCONNECTED',
                            'STOCK_TRANSFER',
                            'STOCKTAKE',
                            'SYNC',
                            'INVENTORY'
                        ))
                    """);
            log.info("Migration: notifications_type_check updated with order workflow notification types");
        } catch (Exception e) {
            log.error("Migration error updating notifications type constraint: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        INSERT INTO system_settings (key, value, description, category, updated_at)
                        VALUES
                        ('store.name', 'OmniSales Store', 'Tên thương hiệu / Cửa hàng', 'STORE', NOW()),
                        ('store.phone', '0987654321', 'Hotline liên hệ', 'STORE', NOW()),
                        ('store.email', 'contact@omnisales.vn', 'Email liên hệ', 'STORE', NOW()),
                        ('store.tax_code', '0101234567', 'Mã số thuế doanh nghiệp', 'STORE', NOW()),
                        ('store.address', '123 Nguyễn Trãi, Thanh Xuân, Hà Nội', 'Địa chỉ trụ sở chính', 'STORE', NOW()),
                        ('inventory.low_stock_threshold', '10', 'Ngưỡng cảnh báo tồn kho tối thiểu', 'INVENTORY', NOW()),
                        ('inventory.allow_negative_stock', 'false', 'Cho phép xuất kho khi tồn kho bằng 0', 'INVENTORY', NOW()),
                        ('inventory.reserved_timeout_minutes', '30', 'Thời gian tự động giải phóng hàng giữ (phút)', 'INVENTORY', NOW()),
                        ('notification_order_enabled', 'true', 'Gửi cảnh báo về đơn hàng', 'NOTIFICATION', NOW()),
                        ('notification_return_enabled', 'true', 'Gửi cảnh báo về yêu cầu trả hàng', 'NOTIFICATION', NOW()),
                        ('notification_low_stock_enabled', 'true', 'Gửi cảnh báo khi tồn kho thấp hoặc hết hàng', 'NOTIFICATION', NOW()),
                        ('notification_sync_failure_enabled', 'true', 'Gửi cảnh báo khi đồng bộ dữ liệu thất bại', 'NOTIFICATION', NOW()),
                        ('notification_channel_disconnected_enabled', 'true', 'Gửi cảnh báo khi kênh bán hàng mất kết nối', 'NOTIFICATION', NOW()),
                        ('notification_email_enabled', 'false', 'Gửi thêm thông báo qua email', 'NOTIFICATION', NOW()),
                        ('notification_retention_days', '90', 'Số ngày lưu thông báo trước khi tự động xóa', 'NOTIFICATION', NOW()),
                        ('account_lock_minutes', '5', 'Thời gian khóa tài khoản sau khi đăng nhập sai (phút)', 'SECURITY', NOW()),
                        ('access_token_expiration_minutes', '1440', 'Thời gian hiệu lực access token (phút)', 'SECURITY', NOW()),
                        ('refresh_token_expiration_days', '7', 'Thời gian hiệu lực refresh token (ngày)', 'SECURITY', NOW()),
                        ('password_min_length', '8', 'Độ dài mật khẩu tối thiểu', 'SECURITY', NOW()),
                        ('password_require_uppercase', 'true', 'Yêu cầu mật khẩu có chữ hoa', 'SECURITY', NOW()),
                        ('password_require_lowercase', 'true', 'Yêu cầu mật khẩu có chữ thường', 'SECURITY', NOW()),
                        ('password_require_number', 'true', 'Yêu cầu mật khẩu có chữ số', 'SECURITY', NOW()),
                        ('password_require_special_character', 'true', 'Yêu cầu mật khẩu có ký tự đặc biệt', 'SECURITY', NOW()),
                        ('password_expiration_days', '90', 'Số ngày mật khẩu có hiệu lực; nhập 0 để không hết hạn', 'SECURITY', NOW()),
                        ('system_name', 'OmniSales', 'Tên hiển thị của hệ thống', 'SYSTEM', NOW()),
                        ('support_email', 'contact@omnisales.vn', 'Email hỗ trợ', 'SYSTEM', NOW()),
                        ('support_phone', '0987654321', 'Số điện thoại hỗ trợ', 'SYSTEM', NOW()),
                        ('business_address', '123 Nguyễn Trãi, Thanh Xuân, Hà Nội', 'Địa chỉ doanh nghiệp', 'SYSTEM', NOW()),
                        ('tax_code', '0101234567', 'Mã số thuế', 'SYSTEM', NOW()),
                        ('date_format', 'dd/MM/yyyy', 'Định dạng ngày tháng mặc định', 'SYSTEM', NOW()),
                        ('default_page_size', '20', 'Số bản ghi mặc định trên mỗi trang', 'SYSTEM', NOW()),
                        ('audit_log_retention_days', '180', 'Số ngày lưu nhật ký hệ thống', 'SYSTEM', NOW()),
                        ('maintenance_mode', 'false', 'Bật chế độ bảo trì hệ thống', 'SYSTEM', NOW()),
                        ('maintenance_message', 'Hệ thống đang bảo trì. Vui lòng thử lại sau.', 'Thông báo hiển thị khi bảo trì', 'SYSTEM', NOW()),
                        ('backup_schedule_enabled', 'true', 'Bật sao lưu dữ liệu tự động hằng ngày', 'SYSTEM', NOW())
                        ON CONFLICT (key) DO NOTHING;
                    """);
            log.info("Migration: seeded default store, inventory, and notification system settings");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for system_settings seeding: {}", e.getMessage());
        }

        // =====================================================
        // Migration: WAITING_STOCK order status
        // =====================================================
        try {
            jdbcTemplate.execute("""
                        DO $$
                        BEGIN
                            IF NOT EXISTS (
                                SELECT 1 FROM pg_type t
                                JOIN pg_enum e ON t.oid = e.enumtypid
                                WHERE t.typname = 'order_status'
                                AND e.enumlabel = 'WAITING_STOCK'
                            ) THEN
                                ALTER TYPE order_status ADD VALUE 'WAITING_STOCK';
                            END IF;
                        END $$;
                    """);
            log.info("Migration: added WAITING_STOCK value to order_status enum");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for WAITING_STOCK enum value: {}", e.getMessage());
        }

        // =====================================================
        // Migration: Add WAITING_STOCK columns to orders table
        // =====================================================
        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS waiting_stock_at TIMESTAMPTZ;
                    """);
            log.info("Migration: added waiting_stock_at column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.waiting_stock_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS waiting_stock_expires_at TIMESTAMPTZ;
                    """);
            log.info("Migration: added waiting_stock_expires_at column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.waiting_stock_expires_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_offer_at TIMESTAMPTZ;
                    """);
            log.info("Migration: added stock_offer_at column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.stock_offer_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_offer_expires_at TIMESTAMPTZ;
                    """);
            log.info("Migration: added stock_offer_expires_at column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.stock_offer_expires_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS stock_offer_notified_at TIMESTAMPTZ;
                    """);
            log.info("Migration: added stock_offer_notified_at column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.stock_offer_notified_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS waiting_stock_expiry_notified_at TIMESTAMPTZ;
                    """);
            log.info("Migration: added waiting_stock_expiry_notified_at column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.waiting_stock_expiry_notified_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS dispatch_sla_at TIMESTAMPTZ;
                    """);
            log.info("Migration: added dispatch_sla_at column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.dispatch_sla_at: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS shipping_due_time TIMESTAMPTZ;
                    """);
            log.info("Migration: added shipping_due_time column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.shipping_due_time: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        ALTER TABLE orders ADD COLUMN IF NOT EXISTS collection_due_time TIMESTAMPTZ;
                    """);
            log.info("Migration: added collection_due_time column to orders");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for orders.collection_due_time: {}", e.getMessage());
        }

        // =====================================================
        // Migration: Add indexes for WAITING_STOCK orders
        // =====================================================
        try {
            jdbcTemplate.execute("""
                        CREATE INDEX IF NOT EXISTS idx_orders_waiting_stock_fifo
                        ON orders (waiting_stock_at, created_at, id)
                        WHERE status = 'WAITING_STOCK';
                    """);
            log.info("Migration: created idx_orders_waiting_stock_fifo index");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for idx_orders_waiting_stock_fifo: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        CREATE INDEX IF NOT EXISTS idx_orders_waiting_stock_expired
                        ON orders (waiting_stock_expires_at, id)
                        WHERE status = 'WAITING_STOCK' AND waiting_stock_expiry_notified_at IS NULL;
                    """);
            log.info("Migration: created idx_orders_waiting_stock_expired index");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for idx_orders_waiting_stock_expired: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        CREATE INDEX IF NOT EXISTS idx_orders_stock_offer_expired
                        ON orders (stock_offer_expires_at, id)
                        WHERE status = 'PENDING' AND stock_offer_at IS NOT NULL;
                    """);
            log.info("Migration: created idx_orders_stock_offer_expired index");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for idx_orders_stock_offer_expired: {}", e.getMessage());
        }

        // =====================================================
        // Migration: Update notifications_type_check with STOCK_OFFER
        // =====================================================
        try {
            Boolean hasStockOffer = jdbcTemplate.queryForObject("""
                        SELECT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'notifications_type_check'
                              AND pg_get_constraintdef(oid) LIKE '%ORDER_STOCK_OFFER%'
                        )
                    """, Boolean.class);
            if (!Boolean.TRUE.equals(hasStockOffer)) {
                jdbcTemplate.execute("""
                            ALTER TABLE notifications
                            DROP CONSTRAINT IF EXISTS notifications_type_check;
                            ALTER TABLE notifications ALTER COLUMN type TYPE VARCHAR(40);
                            ALTER TABLE notifications
                            ADD CONSTRAINT notifications_type_check
                            CHECK (type IN (
                                'LOW_STOCK',
                                'SYNC_FAILED',
                                'ORDER_NEW',
                                'ORDER_CANCELLED',
                                'ORDER_PAID',
                                'ORDER_PICK_REQUIRED',
                                'ORDER_READY_SHIP',
                                'ORDER_SHIPPED',
                                'ORDER_DELIVERED',
                                'ORDER_RETURN_REQUESTED',
                                'ORDER_RETURN_REJECTED',
                                'ORDER_RETURN_COMPLETED',
                                'ORDER_RETURN_ATTENTION',
                                'ORDER_WAITING_STOCK',
                                'ORDER_STOCK_OFFER',
                                'ORDER_WAITING_STOCK_EXPIRED',
                                'ORDER_PLATFORM_STOCK_CONFLICT',
                                'ORDER_BUYER_CANCEL_REQUESTED',
                                'CHANNEL_DISCONNECTED',
                                'STOCK_TRANSFER',
                                'STOCKTAKE',
                                'SYNC',
                                'INVENTORY'
                            ))
                        """);
                log.info("Migration: notifications_type_check updated with ORDER_STOCK_OFFER");
            } else {
                log.info("Migration: notifications_type_check already has ORDER_STOCK_OFFER");
            }
        } catch (Exception e) {
            log.error("Migration error updating notifications_type_check with STOCK_OFFER: {}", e.getMessage());
        }

    }
}
