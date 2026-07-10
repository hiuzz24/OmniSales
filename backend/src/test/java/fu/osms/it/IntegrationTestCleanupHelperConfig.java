package fu.osms.it;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires {@link IntegrationTestCleanupHelper} as a Spring bean so test
 * classes can {@code @Autowired} it (the helper itself is a plain class
 * — not annotated with {@code @Component} — to avoid being picked up
 * in production component scans).
 */
@Configuration
public class IntegrationTestCleanupHelperConfig {

    @Bean
    public IntegrationTestCleanupHelper integrationTestCleanupHelper(JdbcTemplate jdbc) {
        return new IntegrationTestCleanupHelper(jdbc);
    }
}
