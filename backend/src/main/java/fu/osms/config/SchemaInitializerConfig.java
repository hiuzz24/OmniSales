package fu.osms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.init.DataSourceScriptDatabaseInitializer;
import org.springframework.boot.sql.init.DatabaseInitializationMode;
import org.springframework.boot.sql.init.DatabaseInitializationSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.util.List;

/**
 * Schema initializer — tạo DataSourceScriptDatabaseInitializer bean.
 *
 * VẤN ĐỀ NGHIÊM TRỌNG (đã giải quyết):
 *   schema-postgresql.sql chứa PL/pgSQL "$$ ... $$" blocks (DO, CREATE FUNCTION).
 *   Spring Boot ScriptUtils KHÔNG support dollar-quoted strings → silently fail.
 *
 * GIẢI PHÁP:
 *   1. schema-clean.sql đã strip tất cả PL/pgSQL (DO blocks, CREATE FUNCTION, CREATE TRIGGER)
 *      bằng PowerShell regex pre-processing. Chỉ còn CREATE TABLE/INDEX/ENUM/INSERT.
 *   2. Tạo custom DataSourceScriptDatabaseInitializer bean → Spring Boot 3.5 auto-detect
 *      và chạy TRƯỚC Hibernate JPA EntityManagerFactory.
 *   3. Mode=ALWAYS + continueOnError=true → idempotent, an toàn cho Render.
 *
 * @Profile("render") — chỉ chạy trên Render deploy.
 * @DependsOn("dataSource") — đảm bảo DataSource bean ready trước.
 */
@Slf4j
@Configuration
@Profile("render")
public class SchemaInitializerConfig {

    /**
     * Custom DataSourceScriptDatabaseInitializer bean.
     * Spring Boot 3.5 TỰ ĐỘNG detect và chạy TRƯỚC Hibernate JPA.
     */
    @Bean
    @DependsOn("dataSource")
    public DataSourceScriptDatabaseInitializer schemaInitializer(DataSource dataSource) {
        log.info("SchemaInitializerConfig: creating DataSourceScriptDatabaseInitializer for schema-clean.sql");

        DatabaseInitializationSettings settings = new DatabaseInitializationSettings();
        settings.setMode(DatabaseInitializationMode.ALWAYS);
        settings.setSchemaLocations(List.of("classpath:schema-clean.sql"));
        settings.setContinueOnError(true);  // Idempotent: skip duplicate INSERT, recreate OK

        DataSourceScriptDatabaseInitializer initializer =
                new DataSourceScriptDatabaseInitializer(dataSource, settings);

        log.info("SchemaInitializerConfig: DataSourceScriptDatabaseInitializer ready - will run before Hibernate JPA");
        return initializer;
    }
}
