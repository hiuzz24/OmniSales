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
    }
}
