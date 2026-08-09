package fu.osms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Schema initializer — chạy TRƯỚC Hibernate JPA để tạo TẤT Cẩ tables, enums,
 * indexes, FKs từ file schema-postgresql.sql (full schema dump).
 *
 * Cách hoạt động:
 *   1. Tạo custom {@link DataSourceInitializer} bean
 *   2. Spring Boot auto-configuration detect DataSourceInitializer → chạy nó
 *      TRƯỚC khi Hibernate JPA khởi tạo EntityManagerFactory
 *   3. SchemaInitializer logic:
 *        - Check table 'categories' exists
 *        - Nếu KHÔNG: chạy schema-postgresql.sql (idempotent: DROP IF EXISTS ...)
 *        - Nếu CÓ: skip (không chạy SQL)
 *
 * Bean này chỉ tồn tại trên profile "render" — local dev dùng application.yaml
 * riêng (vẫn cho phép ddl-auto validate).
 *
 * Idempotent: schema-postgresql.sql bắt đầu bằng DROP TABLE IF EXISTS nên
 * chạy nhiều lần vẫn OK. Render free tier có thể reset DB → script này sẽ
 * tự dựng lại schema.
 */
@Slf4j
@Configuration
@Profile("render")
public class SchemaInitializerConfig {

    /**
     * Override Spring Boot DataSourceInitializer mặc định.
     *
     * Spring Boot auto-configuration có:
     *   - DataSourceInitializerPostProcessor: detect DataSourceInitializer bean
     *     trong context và run nó TRƯỚC Hibernate JPA init.
     *   - Mặc định bean này không có nếu không bật `spring.sql.init.mode=always`.
     *
     * Bằng cách tạo DataSourceInitializer bean thủ công, ta ép Spring Boot
     * auto-config chạy nó TRƯỚC Hibernate JPA.
     *
     * Logic:
     *   - Check table 'categories' exists.
     *   - Nếu CHƯA: chạy schema-postgresql.sql với ResourceDatabasePopulator.
     *     (script này có DROP IF EXISTS + CREATE TABLE nên idempotent).
     *   - Nếu RỒI: skip.
     */
    @Bean
    @DependsOn("dataSource")
    public DataSourceInitializer dataSourceInitializer(@Qualifier("dataSource") DataSource dataSource) {
        log.info("SchemaInitializerConfig: building DataSourceInitializer for schema-postgresql.sql");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource("schema-postgresql.sql"));
        populator.setIgnoreFailedDrops(true);   // DROP ... IF EXISTS OK
        populator.setContinueOnError(false);     // CREATE TABLE fail → fail-fast

        DataSourceInitializer initializer = new DataSourceInitializer() {
            /**
             * Override createSchema() để check table 'categories' trước khi
             * chạy schema-postgresql.sql. Nếu đã tồn tại → skip, return false.
             */
            @Override
            protected boolean createSchema() {
                try (Connection conn = dataSource.getConnection()) {
                    boolean exists = tableExists(conn, "categories");
                    if (exists) {
                        log.info("SchemaInitializerConfig: table 'categories' already exists — SKIP schema init.");
                        return false;
                    }
                    log.info("SchemaInitializerConfig: table 'categories' MISSING — running schema-postgresql.sql...");
                    boolean result = super.createSchema();
                    if (result) {
                        log.info("SchemaInitializerConfig: schema-postgresql.sql executed successfully.");
                    }
                    return result;
                } catch (Exception e) {
                    log.error("SchemaInitializerConfig: error checking schema state: {}", e.getMessage(), e);
                    return false;
                }
            }
        };
        initializer.setDataSource(dataSource);
        initializer.setDatabasePopulator(populator);
        initializer.setEnabled(true);

        return initializer;
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name = '" + tableName + "')")) {
            return rs.next() && rs.getBoolean(1);
        }
    }
}
