package fu.osms.it;

import fu.osms.address.service.RestCountriesService;
import fu.osms.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for full Spring Boot integration tests.
 *
 * <p>Boots the complete application context against the isolated
 * PostgreSQL test database ({@code osms_it}) on a random port. Provides
 * shared helpers for HTTP calls, JWT login, and DB cleanup.</p>
 *
 * <p>External integrations (Gmail SMTP, RestCountries API) are mocked
 * with {@code @MockBean} so tests stay hermetic.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({SeedDataInitializer.class, TestExternalServicesConfig.class, IntegrationTestCleanupHelperConfig.class})
public abstract class IntegrationTestBase {

    /** Prevents RestCountries API calls from failing. */
    @MockBean
    protected RestCountriesService restCountriesService;

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository userRepository;

    /**
     * Cached tokens per class. Cleared per-test via {@link #cleanUserData()}
     * only resets DB state — JWT tokens remain valid for the duration of the JVM.
     */
    protected String adminToken;
    protected String ownerToken;
    protected String operationsToken;
    protected String salesToken;
    protected String baseUrl;

    @BeforeEach
    void baseSetup() {
        // Resolve the randomly-assigned port for direct HTTP calls.
        this.baseUrl = rest.getRootUri();

        // Lazy-login: cache one token per role per JVM.
        if (adminToken == null)       adminToken       = login("admin@osms.vn",   "11111111");
        if (ownerToken == null)       ownerToken       = login("manager@osms.vn", "11111111");
        if (operationsToken == null)  operationsToken  = login("staff@osms.vn",   "11111111");
        if (salesToken == null)      salesToken       = login("viewer@osms.vn",  "11111111");

        // Reset DB state between tests, but preserve seeded master data.
        cleanUserData();
    }

    /**
     * Truncate transactional / test-created tables between tests.
     * Seeded data (users, roles, categories, warehouses, suppliers,
     * channels, system_settings) is preserved.
     *
     * <p>Uses TRUNCATE ... RESTART IDENTITY CASCADE for speed.</p>
     */
    protected void cleanUserData() {
        IntegrationTestCleanupHelper.runOn(jdbc);
    }

    /**
     * Perform a real /api/auth/login and return the Bearer token.
     */
    protected String login(String email, String password) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        @SuppressWarnings("rawtypes")
        Map response = rest.postForObject("/api/auth/login", new HttpEntity<>(body, h), Map.class);
        assertThat(response).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.get("data");
        return (String) data.get("accessToken");
    }

    /**
     * Build an Authorization header for the given token (default: OWNER).
     */
    protected HttpHeaders authHeader() {
        return authHeader(ownerToken);
    }

    protected HttpHeaders authHeader(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token);
        return h;
    }

    /**
     * Convenience: GET /path with OWNER auth.
     */
    protected <T> T get(String path, Class<T> type) {
        return rest.exchange(path, org.springframework.http.HttpMethod.GET,
                new HttpEntity<>(authHeader()), type).getBody();
    }

    protected <T> T post(String path, Object body, Class<T> type) {
        return rest.exchange(path, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, authHeader()), type).getBody();
    }
}
