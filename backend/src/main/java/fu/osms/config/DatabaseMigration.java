package fu.osms.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseMigration {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
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
                                CHECK (action IN ('CREATE', 'UPDATE', 'DELETE', 'LOGIN', 'LOGOUT', 'EXPORT', 'CONNECT', 'DISCONNECT', 'STATUS_CHANGE', 'ORDER_CANCEL', 'PAYMENT_STATUS_CHANGE'))
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
            if (Boolean.TRUE.equals(hasStatusChange) && Boolean.TRUE.equals(hasPaymentStatusChange)) {
                log.info(
                        "Migration: audit_logs_action_check constraint updated with STATUS_CHANGE and PAYMENT_STATUS_CHANGE");
            } else {
                log.error("Migration FAILED: audit_logs_action_check missing PAYMENT_STATUS_CHANGE!");
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
                        ALTER TABLE stock_transfers ADD COLUMN IF NOT EXISTS note TEXT
                    """);
            log.info("Migration: added note column to stock_transfers table");
        } catch (Exception e) {
            log.warn("Migration skipped or already applied for stock_transfers note: {}", e.getMessage());
        }

        try {
            jdbcTemplate.execute("""
                        UPDATE inventory_items
                        SET quantity_on_hand = GREATEST(quantity_on_hand, 0),
                            reserved_quantity = LEAST(GREATEST(reserved_quantity, 0), GREATEST(quantity_on_hand, 0))
                        WHERE quantity_on_hand < 0
                           OR reserved_quantity < 0
                           OR reserved_quantity > quantity_on_hand
                    """);
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

    }
}
