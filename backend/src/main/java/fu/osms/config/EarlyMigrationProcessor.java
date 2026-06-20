package fu.osms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

import java.sql.DriverManager;

public class EarlyMigrationProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(EarlyMigrationProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        String username = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");

        if (url == null || username == null) return;

        try {
            Class.forName("org.postgresql.Driver");
            try (var conn = DriverManager.getConnection(url, username, password);
                 var stmt = conn.createStatement()) {
                stmt.execute("""
                    ALTER TABLE customers
                    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT true
                """);
                log.info("EarlyMigration: added is_active column to customers table");
            }
        } catch (Exception e) {
            log.warn("EarlyMigration: failed or column already exists: {}", e.getMessage());
        }
    }
}
