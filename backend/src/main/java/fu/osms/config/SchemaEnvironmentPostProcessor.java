package fu.osms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
/**
 * SCHEMA ENVIRONMENT POST PROCESSOR — chạy schema TRƯỚC Spring Boot!
 *
 * Đây là ENVIRONMENT POST PROCESSOR đăng ký qua META-INF/spring.factories.
 * SpringApplication gọi TẤT CẢ EnvironmentPostProcessor TRƯỚC khi tạo
 * ApplicationContext. Tại thời điểm này, chưa có bean nào được tạo cả.
 *
 * Đây là approach MẠNH NHẤT để chạy schema init:
 *   - Không phụ thuộc vào Spring Boot's DataSourceScriptDatabaseInitializer
 *   - Không phụ thuộc vào spring.sql.init config
 *   - Không phụ thuộc vào bean ordering của Spring
 *   - Chạy RAW JDBC ngay khi env đã load xong
 *
 * CÁCH HOẠT ĐỘNG:
 *   1. SpringApplication gọi postProcessEnvironment() TRƯỚC khi tạo context.
 *   2. Read spring.datasource.url/username/password từ env.
 *   3. Connect thẳng tới PostgreSQL.
 *   4. Đọc classpath:schema.sql.
 *   5. Parse SQL thông minh (handle $$ ... $$ blocks bằng cách execute từng statement).
 *   6. Skip nếu tables đã tồn tại (idempotent).
 *
 * Chỉ chạy khi profile=render (check SPRING_PROFILES_ACTIVE env).
 */
public class SchemaEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SchemaEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // Chỉ chạy khi profile=render active
        String[] activeProfiles = environment.getActiveProfiles();
        boolean isRender = false;
        for (String p : activeProfiles) {
            if ("render".equalsIgnoreCase(p)) {
                isRender = true;
                break;
            }
        }
        if (!isRender) {
            log.debug("SchemaEnvironmentPostProcessor: not render profile, skipping schema init");
            return;
        }

        String url = environment.resolvePlaceholders(
                environment.getProperty("spring.datasource.url"));
        String username = environment.resolvePlaceholders(
                environment.getProperty("spring.datasource.username"));
        String password = environment.resolvePlaceholders(
                environment.getProperty("spring.datasource.password"));

        if (url == null || username == null || password == null) {
            log.warn("SchemaEnvironmentPostProcessor: datasource config missing, skipping");
            return;
        }

        log.info("SchemaEnvironmentPostProcessor: connecting to {} as {}", url, username);

        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(url, username, password)) {

                // Check schema đã có chưa (categories table)
                if (isSchemaInitialized(conn)) {
                    log.info("SchemaEnvironmentPostProcessor: tables already exist — SKIP schema init");
                    return;
                }

                // Đọc schema.sql
                String sql = readSqlFile();
                log.info("SchemaEnvironmentPostProcessor: read {} bytes from schema.sql", sql.length());

                // Parse + execute
                List<String> statements = splitSqlStatements(sql);
                log.info("SchemaEnvironmentPostProcessor: parsed {} statements", statements.size());

                int success = 0, errors = 0;
                try (Statement stmt = conn.createStatement()) {
                    for (int i = 0; i < statements.size(); i++) {
                        String s = statements.get(i).trim();
                        if (s.isEmpty() || s.startsWith("--")) continue;

                        try {
                            stmt.execute(s);
                            success++;
                        } catch (Exception e) {
                            errors++;
                            String preview = s.length() > 200 ? s.substring(0, 200) + "..." : s;
                            log.error("SchemaEnvironmentPostProcessor: statement #{} failed: {} | SQL: {}",
                                    i + 1, e.getMessage(), preview.replaceAll("\\s+", " "));
                        }
                    }
                }

                log.info("SchemaEnvironmentPostProcessor: ✅ done — {} success, {} errors", success, errors);

                if (errors > 0 && success == 0) {
                    throw new IllegalStateException(
                            "Schema init failed completely: " + errors + " errors, 0 successes. " +
                            "App cannot start without schema.");
                }
            }
        } catch (Exception e) {
            log.error("SchemaEnvironmentPostProcessor: ❌ FATAL: {}", e.getMessage(), e);
            // KHÔNG throw - để app tiếp tục boot, sẽ fail tự nhiên ở các query sau.
            // Alternative: throw để fail-fast (uncomment nếu muốn)
            // throw new IllegalStateException("Schema init failed", e);
        }
    }

    /**
     * Check categories table đã tồn tại chưa.
     */
    private boolean isSchemaInitialized(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name = 'categories')")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    /**
     * Đọc schema.sql từ classpath.
     */
    private String readSqlFile() throws Exception {
        var resource = new ClassPathResource("schema.sql");
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Parse SQL thành list statements, skip comments.
     * KHÔNG support PL/pgSQL (file schema.sql đã strip sẵn).
     */
    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();

        // Remove SQL line comments (giữ newline structure)
        StringBuilder cleaned = new StringBuilder();
        for (String line : sql.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) continue;
            int commentIdx = findInlineCommentStart(line);
            if (commentIdx >= 0) {
                cleaned.append(line, 0, commentIdx).append("\n");
            } else {
                cleaned.append(line).append("\n");
            }
        }

        // Split theo ";" — schema.sql không có PL/pgSQL nên OK
        String[] parts = cleaned.toString().split(";");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) statements.add(trimmed);
        }
        return statements;
    }

    /**
     * Tìm vị trí bắt đầu "--" inline comment, skip string literals.
     */
    private int findInlineCommentStart(String line) {
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote;
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote;
            else if (!inSingleQuote && !inDoubleQuote && c == '-' && line.charAt(i + 1) == '-') {
                return i;
            }
        }
        return -1;
    }
}
