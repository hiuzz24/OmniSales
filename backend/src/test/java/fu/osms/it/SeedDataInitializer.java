package fu.osms.it;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent test-data verification. The {@code db-init.ps1} /
 * {@code db-init.sh} scripts already seed the master data set
 * (roles, users, categories, warehouses, suppliers, channels,
 * system_settings) from {@code backend/hibernate-schema.sql}; this
 * component just verifies the seed loaded successfully and prints a
 * short report so a mis-configured test DB is obvious in the logs.
 *
 * <p>It is registered automatically via the
 * {@code @SpringBootApplication} component scan because it lives in
 * the {@code fu.osms} test package and {@code @TestConfiguration} is
 * also picked up.</p>
 */
@TestConfiguration
public class SeedDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(SeedDataInitializer.class);

    /**
     * Verifies the master data set is present. Returns a small report
     * which the test base logs in {@code @BeforeAll}.
     */
    @Bean
    @Primary
    public SeedReport seedReport(JdbcTemplate jdbc) {
        SeedReport r = new SeedReport();
        r.users = count(jdbc, "users");
        r.roles = count(jdbc, "roles");
        r.userRoles = count(jdbc, "user_roles");
        r.categories = count(jdbc, "categories");
        r.warehouses = count(jdbc, "warehouses");
        r.suppliers = count(jdbc, "suppliers");
        r.channels = count(jdbc, "channels");
        r.systemSettings = count(jdbc, "system_settings");
        log.info("[IT-seed] master data: users={} roles={} userRoles={} categories={} warehouses={} suppliers={} channels={} systemSettings={}",
                r.users, r.roles, r.userRoles, r.categories, r.warehouses, r.suppliers, r.channels, r.systemSettings);
        if (r.users < 4 || r.roles < 4) {
            log.warn("[IT-seed] Expected >=4 users and >=4 roles. Did you run db-init.ps1?");
        }
        return r;
    }

    private int count(JdbcTemplate jdbc, String table) {
        try {
            Integer v = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            return v == null ? 0 : v;
        } catch (Exception e) {
            return -1;
        }
    }

    public static class SeedReport {
        public int users;
        public int roles;
        public int userRoles;
        public int categories;
        public int warehouses;
        public int suppliers;
        public int channels;
        public int systemSettings;
    }
}
