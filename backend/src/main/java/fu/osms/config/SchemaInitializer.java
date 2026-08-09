package fu.osms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * Schema initializer — chạy TRƯỚC Hibernate JPA để tạo TẤT CẢ tables, enums,
 * indexes, FKs từ file schema-postgresql.sql (full schema dump).
 *
 * Lý do cần bean này:
 *   Hibernate ddl-auto KHÔNG tự tạo custom ENUM types và KHÔNG generate FK/index
 *   statements chuẩn cho PostgreSQL. Ngoài ra, Render Postgres có thể reset DB
 *   về empty → cần tạo đầy đủ 49 tables + ENUMs + indexes + FKs TRƯỚC khi
 *   Hibernate scan, @Async seeders chạy.
 *
 * Bean này implement InitializingBean + @DependsOn DataSource → Spring gọi
 * afterPropertiesSet() NGAY SAU khi DataSource ready, TRƯỚC khi Hibernate JPA
 * EntityManagerFactory được khởi tạo. Bean này chỉ tồn tại trên profile "render"
 * — local dev dùng application.yaml riêng (vẫn cho phép ddl-auto validate).
 *
 * Idempotent: schema-postgresql.sql bắt đầu bằng DROP TABLE IF EXISTS nên
 * chạy nhiều lần vẫn OK. Render free tier có thể reset DB → script này sẽ
 * tự dựng lại schema.
 */
@Slf4j
@Configuration
@Profile("render")
@DependsOn("dataSource")
@RequiredArgsConstructor
public class SchemaInitializer implements InitializingBean {

    private final DataSource dataSource;

    @Override
    public void afterPropertiesSet() throws Exception {
        log.info("SchemaInitializer: checking PostgreSQL schema state...");

        try {
            // 1. Kiểm tra bảng "categories" đã tồn tại chưa.
            //    Dùng information_schema thay vì SELECT trực tiếp.
            boolean schemaExists = checkTableExists("categories");

            if (schemaExists) {
                log.info("SchemaInitializer: table 'categories' already exists. Skipping schema init.");
                return;
            }

            log.info("SchemaInitializer: table 'categories' MISSING. Running full schema-postgresql.sql...");

            // 2. Chạy full schema via JDBC. ScriptUtils tách theo dấu ;
            //    một cách an toàn cho Postgres.
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource("schema-postgresql.sql"));
            populator.setIgnoreFailedDrops(true);   // DROP ... IF EXISTS OK
            populator.setContinueOnError(false);     // CREATE TABLE fail → throw (fail-fast)
            populator.execute(dataSource);

            log.info("SchemaInitializer: schema-postgresql.sql executed successfully.");

            // 3. Verify lại sau khi chạy
            if (checkTableExists("categories")) {
                log.info("SchemaInitializer: verified — table 'categories' now exists.");
            } else {
                log.error("SchemaInitializer: FAILED — table 'categories' STILL missing after init!");
            }

        } catch (Exception e) {
            log.error("SchemaInitializer: CRITICAL error initializing schema: {}", e.getMessage(), e);
            // KHÔNG throw ra ngoài — để app vẫn boot, log cho biết lỗi.
            // Nếu throw, app không start được.
        }
    }

    private boolean checkTableExists(String tableName) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement();
             var rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT 1 FROM information_schema.tables " +
                     "WHERE table_schema = 'public' AND table_name = '" + tableName + "')")) {
            return rs.next() && rs.getBoolean(1);
        } catch (Exception e) {
            log.warn("SchemaInitializer: cannot check table existence: {}", e.getMessage());
            return false;
        }
    }
}
