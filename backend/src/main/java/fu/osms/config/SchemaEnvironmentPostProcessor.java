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
 * Đăng ký qua META-INF/spring.factories. SpringApplication gọi TẤT CẢ
 * EnvironmentPostProcessor TRƯỚC khi tạo ApplicationContext.
 *
 * WORKFLOW:
 *   1. SpringApplication.prepareEnvironment() → load application.yaml + render profile + env vars
 *   2. environment.resolvePlaceholders() để interpolate ${DB_HOST:...}
 *   3. Connect trực tiếp PostgreSQL, check tables tồn tại chưa
 *   4. Nếu chưa → chạy schema.sql qua raw JDBC
 *   5. Nếu có lỗi → log FATAL nhưng KHÔNG throw (app vẫn boot để debug)
 *
 * Quan trọng: profile render phải được check từ environment.getActiveProfiles()
 * SAU KHI Spring load xong application-render.yml.
 */
public class SchemaEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(SchemaEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        log.info("==== SchemaEnvironmentPostProcessor START ====");

        // 1. Check profile
        String[] activeProfiles = environment.getActiveProfiles();
        log.info("SchemaEnvironmentPostProcessor: active profiles = {}", String.join(",", activeProfiles));

        boolean isRender = false;
        for (String p : activeProfiles) {
            if ("render".equalsIgnoreCase(p)) {
                isRender = true;
                break;
            }
        }
        if (!isRender) {
            log.info("SchemaEnvironmentPostProcessor: not render profile, skipping schema init");
            return;
        }

        // 2. Resolve datasource config từ env
        String url = environment.resolvePlaceholders(
                environment.getProperty("spring.datasource.url"));
        String username = environment.resolvePlaceholders(
                environment.getProperty("spring.datasource.username"));
        String password = environment.resolvePlaceholders(
                environment.getProperty("spring.datasource.password"));

        log.info("SchemaEnvironmentPostProcessor: url={}, username={}, password={}",
                url, username, password != null ? "***SET***" : "NULL");

        if (url == null || username == null || password == null) {
            log.error("SchemaEnvironmentPostProcessor: ❌ datasource config missing! url={}, username={}, password={}",
                    url, username, password != null ? "***SET***" : "NULL");
            return;
        }

        // 3. Connect + check schema + init
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                log.info("SchemaEnvironmentPostProcessor: ✅ DB connected");

                if (isSchemaInitialized(conn)) {
                    log.info("SchemaEnvironmentPostProcessor: tables already exist — SKIP schema init");
                    return;
                }

                String sql = readSqlFile();
                log.info("SchemaEnvironmentPostProcessor: read {} bytes from schema.sql", sql.length());

                List<String> statements = splitSqlStatements(sql);
                log.info("SchemaEnvironmentPostProcessor: parsed {} SQL statements", statements.size());

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
                            String preview = s.length() > 150 ? s.substring(0, 150) + "..." : s;
                            log.error("SchemaEnvironmentPostProcessor: ❌ stmt #{} failed: {} | SQL: {}",
                                    i + 1, e.getMessage(), preview.replaceAll("\\s+", " "));
                        }
                    }
                }

                log.info("SchemaEnvironmentPostProcessor: ✅ DONE — {} success, {} errors", success, errors);

                if (errors > 0 && success == 0) {
                    log.error("SchemaEnvironmentPostProcessor: ❌❌❌ ALL statements FAILED. App sẽ fail khi query.");
                } else if (success > 0) {
                    // Verify lại tables đã tồn tại chưa
                    if (isSchemaInitialized(conn)) {
                        log.info("SchemaEnvironmentPostProcessor: ✅✅✅ VERIFIED: categories table exists!");
                    } else {
                        log.error("SchemaEnvironmentPostProcessor: ❌ categories table STILL missing!");
                    }
                }
            }
        } catch (Exception e) {
            log.error("SchemaEnvironmentPostProcessor: ❌ FATAL: {}", e.getMessage(), e);
        }

        log.info("==== SchemaEnvironmentPostProcessor END ====");
    }

    private boolean isSchemaInitialized(Connection conn) throws Exception {
        try (Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name = 'categories')")) {
            return rs.next() && rs.getBoolean(1);
        }
    }

    private String readSqlFile() throws Exception {
        var resource = new ClassPathResource("schema.sql");
        try (var is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();

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

        String[] parts = cleaned.toString().split(";");
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) statements.add(trimmed);
        }
        return statements;
    }

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
