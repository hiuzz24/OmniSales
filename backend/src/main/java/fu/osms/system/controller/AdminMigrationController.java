package fu.osms.system.controller;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;

/**
 * One-shot admin endpoint để apply migration 20260810_webhook_retry_and_order_pull_jobs
 * lên Postgres Render (free plan không có psql Shell).
 *
 * <p><b>Security</b>: chỉ chạy khi env {@code ADMIN_MIGRATION_KEY} được set trên Render
 * và request phải gửi header {@code X-Admin-Migration-Key} khớp đúng giá trị đó.
 * Sau khi chạy xong, <b>xóa env {@code ADMIN_MIGRATION_KEY} + redeploy</b> để
 * endpoint vô hiệu hóa hoàn toàn.
 *
 * <p>Endpoint này <b>không</b> nên tồn tại lâu dài — chỉ dùng cho migration một lần.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/run-migrations")
public class AdminMigrationController {

    @Value("${app.admin.migration-key:#{null}}")
    private String configuredKey;

    @PersistenceContext
    private EntityManager em;

    /**
     * DEBUG endpoint: trả về thông tin DB mà service đang nối tới:
     *  - current_database()
     *  - current_schema(), current_schemas()
     *  - schema của webhook_events (search_path thực tế dùng để resolve name)
     *  - schema của order_pull_jobs
     *  - Có column retry_count trên webhook_events (qualified vs unqualified).
     *
     *  Dùng khi service log lỗi "column does not exist" trong khi
     *  AdminMigrationController verify thấy "EXISTS" — để xác định xem
     *  service có đang nối DB khác, schema khác, hay column đang thực sự thiếu.
     */
    @PostMapping("/debug-schema-info")
    @Transactional
    public ResponseEntity<Map<String, Object>> debugSchemaInfo(
            @RequestHeader(value = "X-Admin-Migration-Key", required = false) String headerKey) {
        // Same security gate as the main migration endpoint.
        if (configuredKey == null || configuredKey.isBlank()) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DISABLED",
                    "message", "ADMIN_MIGRATION_KEY env var is not set."));
        }
        if (headerKey == null || !headerKey.equals(configuredKey)) {
            return ResponseEntity.status(403).body(Map.of("status", "FORBIDDEN"));
        }

        Map<String, Object> info = new LinkedHashMap<>();
        try {
            info.put("current_database", scalarString(
                    "SELECT current_database()"));
            info.put("current_schema", scalarString(
                    "SELECT current_schema()"));
            info.put("current_user", scalarString(
                    "SELECT current_user"));
            info.put("search_path", scalarString(
                    "SELECT current_setting('search_path')"));
            info.put("server_version_num", scalarString(
                    "SELECT current_setting('server_version_num')"));
            info.put("server_version", scalarString(
                    "SELECT version()"));

            // Where does PostgreSQL resolve 'webhook_events' to?
            // Use listStrings in case multiple schemas have webhook_events
            info.put("webhook_events.resolved_schema", listStrings(
                    "SELECT schemaname || '.' || tablename " +
                    "FROM pg_tables WHERE tablename = 'webhook_events'"));
            info.put("webhook_events.schema_multiple", listStrings(
                    "SELECT schemaname || '.' || tablename " +
                    "FROM pg_tables WHERE tablename = 'webhook_events'"));

            // Same for order_pull_jobs
            info.put("order_pull_jobs.schema_multiple", listStrings(
                    "SELECT schemaname || '.' || tablename " +
                    "FROM pg_tables WHERE tablename = 'order_pull_jobs'"));

            // Does the resolved table have retry_count?
            info.put("webhook_events.has_retry_count", listStrings(
                    "SELECT table_schema || '.' || table_name || '.' || column_name " +
                    "FROM information_schema.columns " +
                    "WHERE column_name = 'retry_count' AND table_name = 'webhook_events'"));

            // Diagnose: count webhook_events rows
            info.put("webhook_events_row_count", scalarString(
                    "SELECT COUNT(*) FROM webhook_events"));

            // Same for purchase_orders.evidence_url
            info.put("purchase_orders_evidence_url_locations", listStrings(
                    "SELECT table_schema || '.' || table_name || '.' || column_name " +
                    "FROM information_schema.columns " +
                    "WHERE column_name = 'evidence_url' AND table_name = 'purchase_orders'"));

            // === EXACT replica of WebhookEventRetrySweeper query ===
            // The native SQL thrown the "column retry_count does not exist" error.
            // If this works, the actual sweeper should also work — meaning the
            // exception must have been from a stale JVM/cache pre-redeploy.
            try {
                List<?> replica = em.createNativeQuery(
                        "SELECT id\n" +
                        "FROM webhook_events\n" +
                        "WHERE (\n" +
                        "        (\n" +
                        "          status IN ('RECEIVED', 'PROCESSING')\n" +
                        "          AND received_at <= ?\n" +
                        "        )\n" +
                        "        OR\n" +
                        "        (\n" +
                        "          status = 'FAILED'\n" +
                        "          AND retry_count < ?\n" +
                        "          AND channel_id IS NOT NULL\n" +
                        "        )\n" +
                        "      )\n" +
                        "ORDER BY received_at ASC\n" +
                        "FOR UPDATE SKIP LOCKED\n" +
                        "LIMIT ?\n")
                        .setParameter(1, OffsetDateTime.now().minusDays(7))
                        .setParameter(2, 3)
                        .setParameter(3, 50)
                        .getResultList();
                info.put("sweeper_replica_query.rows", replica.size());
                info.put("sweeper_replica_query.ok", true);
            } catch (Exception ex) {
                info.put("sweeper_replica_query.ok", false);
                info.put("sweeper_replica_query.error",
                        ex.getClass().getSimpleName() + ": " + ex.getMessage());
            }

            info.put("ok", true);
        } catch (Exception e) {
            info.put("ok", false);
            info.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        return ResponseEntity.ok(info);
    }

    private String scalarString(String sql) {
        Object r = em.createNativeQuery(sql).getSingleResult();
        return r == null ? null : r.toString();
    }

    @SuppressWarnings("unchecked")
    private List<String> listStrings(String sql) {
        return (List<String>) (List<?>) em.createNativeQuery(sql).getResultList();
    }

    /**
     * Apply migration 20260810_webhook_retry_and_order_pull_jobs.
     *
     * <p>Idempotent — có thể gọi nhiều lần (dùng {@code IF NOT EXISTS}, {@code DROP TABLE IF EXISTS}).
     */
    @PostMapping("/20260810-webhook-retry-and-order-pull-jobs")
    @Transactional
    public ResponseEntity<Map<String, Object>> run20260810(
            @RequestHeader(value = "X-Admin-Migration-Key", required = false) String headerKey) {

        // 1. Security: env var phải được set
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("AdminMigrationController invoked but ADMIN_MIGRATION_KEY is not configured — endpoint disabled");
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DISABLED",
                    "message", "ADMIN_MIGRATION_KEY env var is not set. Set it on Render to enable this endpoint."));
        }
        // 2. Security: key trong header phải khớp
        if (headerKey == null || !headerKey.equals(configuredKey)) {
            log.warn("AdminMigrationController invoked with invalid key");
            return ResponseEntity.status(403).body(Map.of(
                    "status", "FORBIDDEN",
                    "message", "X-Admin-Migration-Key header is missing or invalid."));
        }

        log.info("Running migration 20260810_webhook_retry_and_order_pull_jobs...");
        List<Map<String, Object>> results = new ArrayList<>();
        boolean allOk = true;

        // ----- 1. webhook_events.retry_count -----
        results.add(executeDdl(
                "ALTER TABLE webhook_events ADD COLUMN IF NOT EXISTS retry_count INT NOT NULL DEFAULT 0"));

        // ----- 2. order_pull_jobs -----
        results.add(executeDdl("DROP TABLE IF EXISTS order_pull_jobs CASCADE"));

        results.add(executeDdl("""
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
                )"""));

        results.add(executeDdl("""
                CREATE INDEX IF NOT EXISTS idx_order_pull_jobs_recovery
                    ON order_pull_jobs (state, updated_at)
                    WHERE state IN ('PENDING', 'PUBLISHED', 'PROCESSING')"""));

        // ----- 3. purchase_orders.evidence_url -----
        results.add(executeDdl(
                "ALTER TABLE purchase_orders ADD COLUMN IF NOT EXISTS evidence_url TEXT"));

        // ----- 4. Drop 1-1 unique constraint on inventory_receipts.purchase_order_id -----
        results.add(executeDdl("""
                DO $$
                BEGIN
                    IF EXISTS (
                        SELECT 1 FROM pg_constraint WHERE conname = 'uq_inventory_receipt_purchase_order'
                    ) THEN
                        ALTER TABLE inventory_receipts
                            DROP CONSTRAINT uq_inventory_receipt_purchase_order;
                    END IF;
                END $$"""));

        // ----- 5. Verification -----
        Map<String, Object> verify = verifyMigration();
        results.add(Map.of(
                "statement", "VERIFY",
                "ok", verify.get("ok"),
                "detail", verify));

        for (Map<String, Object> r : results) {
            if (Boolean.FALSE.equals(r.get("ok"))) {
                allOk = false;
                break;
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("migration", "20260810_webhook_retry_and_order_pull_jobs");
        body.put("status", allOk ? "OK" : "PARTIAL_FAILURE");
        body.put("executedStatements", results);
        if (allOk) {
            body.put("nextStep", "Migration applied. You should now DELETE env ADMIN_MIGRATION_KEY on Render and redeploy to disable this endpoint.");
        } else {
            body.put("nextStep", "Check failed statement in executedStatements above.");
        }
        log.info("Migration 20260810 finished with status={}", body.get("status"));
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> executeDdl(String ddl) {
        Map<String, Object> result = new LinkedHashMap<>();
        // Truncate ddl để hiển thị gọn
        String preview = ddl.length() > 80 ? ddl.substring(0, 80) + "..." : ddl;
        result.put("statement", preview);
        try {
            em.createNativeQuery(ddl).executeUpdate();
            result.put("ok", true);
            log.info("Migration step OK: {}", preview);
        } catch (Exception e) {
            result.put("ok", false);
            result.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("Migration step FAILED: {} -> {}", preview, e.getMessage());
        }
        return result;
    }

    private Map<String, Object> verifyMigration() {
        Map<String, Object> v = new LinkedHashMap<>();
        try {
            // 1. retry_count column tồn tại
            Object retryCountCol = em.createNativeQuery(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'webhook_events' AND column_name = 'retry_count'")
                    .getSingleResult();
            v.put("webhook_events.retry_count", retryCountCol != null ? "EXISTS" : "MISSING");

            // 2. order_pull_jobs tồn tại
            Object orderPullJobsCount = em.createNativeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables " +
                    "WHERE table_name = 'order_pull_jobs'")
                    .getSingleResult();
            v.put("order_pull_jobs table", orderPullJobsCount);

            // 3. evidence_url column tồn tại
            Object evidenceUrlCol = em.createNativeQuery(
                    "SELECT column_name FROM information_schema.columns " +
                    "WHERE table_name = 'purchase_orders' AND column_name = 'evidence_url'")
                    .getSingleResult();
            v.put("purchase_orders.evidence_url", evidenceUrlCol != null ? "EXISTS" : "MISSING");

            v.put("ok", true);
        } catch (Exception e) {
            v.put("ok", false);
            v.put("error", e.getMessage());
        }
        return v;
    }
}
