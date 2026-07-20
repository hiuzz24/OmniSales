package fu.osms.it;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Utility for cleaning the test DB between integration tests.
 *
 * <p>Autowire the bean — see {@link IntegrationTestCleanupHelperConfig}
 * for the wiring. For static call sites use {@link #runOn(JdbcTemplate)}.</p>
 */
public class IntegrationTestCleanupHelper {

    private static final String TRUNCATE_SQL = "TRUNCATE TABLE " +
            "audit_logs, " +
            "order_items, orders, " +
            "customer_platform_ids, customers, " +
            "inventory_issue_items, inventory_issues, " +
            "inventory_receipt_items, inventory_receipts, " +
            "inventory_items, " +
            "stock_transfer_items, stock_transfers, " +
            "stocktake_items, stocktake_sessions, " +
            "product_logs, product_images, product_variants, products, " +
            "channel_product_variants, channel_products, " +
            "channel_connection_logs, channel_credentials, " +
            "sync_logs, sync_tasks, webhook_events, " +
            "notifications, system_logs, api_metrics_daily, " +
            "report_results, report_configs, " +
            "backup_files, password_reset_tokens, user_invite_tokens " +
            "RESTART IDENTITY CASCADE";

    private static final String RESET_USERS_SQL =
            "UPDATE users SET failed_login_attempts = 0, locked_until = NULL, password_expired = false";

    private final JdbcTemplate jdbc;

    public IntegrationTestCleanupHelper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Static convenience: run the standard truncate on any {@link JdbcTemplate}. */
    public static void runOn(JdbcTemplate jdbc) {
        jdbc.execute(TRUNCATE_SQL);
        jdbc.execute(RESET_USERS_SQL);
    }

    /**
     * Truncate all user-mutable tables. Seeded reference data
     * (roles, users, categories, warehouses, suppliers, channels,
     * system_settings) is preserved.
     *
     * <p>Uses TRUNCATE ... RESTART IDENTITY CASCADE for speed.</p>
     */
    public void truncate() {
        runOn(jdbc);
    }
}
