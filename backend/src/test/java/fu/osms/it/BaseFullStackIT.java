package fu.osms.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.auth.security.JwtService;
import fu.osms.common.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack Integration Test base. Boots the entire Spring context
 * (PostgreSQL, security filter chain, JWT, scheduled jobs) and exposes
 * high-level HTTP helpers so individual test classes can focus on
 * domain scenarios rather than HTTP plumbing.
 *
 * <p>Extends {@link IntegrationTestBase} so the JWT login, cleanup and
 * token-caching behaviour is inherited. The base class already mocks
 * Gmail SMTP and RestCountries so the suite stays hermetic.</p>
 */
public abstract class BaseFullStackIT extends IntegrationTestBase {

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected fu.osms.auth.repository.UserRepository users;

    // ────────────────────────────────────────────────────────────────────
    // High-level HTTP helpers
    // ────────────────────────────────────────────────────────────────────

    /** GET as OWNER (default role). */
    protected <T> ResponseEntity<T> getAsOwner(String path, Class<T> type) {
        return exchange(HttpMethod.GET, path, ownerToken, null, type);
    }

    /** GET as SYSTEM_ADMIN. */
    protected <T> ResponseEntity<T> getAsAdmin(String path, Class<T> type) {
        return exchange(HttpMethod.GET, path, adminToken, null, type);
    }

    /** GET as OPERATIONS. */
    protected <T> ResponseEntity<T> getAsOperations(String path, Class<T> type) {
        return exchange(HttpMethod.GET, path, operationsToken, null, type);
    }

    /** GET as SALES. */
    protected <T> ResponseEntity<T> getAsSales(String path, Class<T> type) {
        return exchange(HttpMethod.GET, path, salesToken, null, type);
    }

    /** GET without auth header (used for 401/403 negative tests). */
    protected <T> ResponseEntity<T> getAnonymous(String path, Class<T> type) {
        return exchange(HttpMethod.GET, path, null, null, type);
    }

    protected <T> ResponseEntity<T> postAsOwner(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.POST, path, ownerToken, body, type);
    }

    protected <T> ResponseEntity<T> postAsAdmin(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.POST, path, adminToken, body, type);
    }

    protected <T> ResponseEntity<T> postAsOperations(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.POST, path, operationsToken, body, type);
    }

    protected <T> ResponseEntity<T> postAnonymous(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.POST, path, null, body, type);
    }

    protected <T> ResponseEntity<T> putAsOwner(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.PUT, path, ownerToken, body, type);
    }

    protected <T> ResponseEntity<T> putAsAdmin(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.PUT, path, adminToken, body, type);
    }

    protected <T> ResponseEntity<T> deleteAsOwner(String path, Class<T> type) {
        return exchange(HttpMethod.DELETE, path, ownerToken, null, type);
    }

    protected <T> ResponseEntity<T> deleteAsAdmin(String path, Class<T> type) {
        return exchange(HttpMethod.DELETE, path, adminToken, null, type);
    }

    protected <T> ResponseEntity<T> patchAsOwner(String path, Object body, Class<T> type) {
        return exchange(HttpMethod.PATCH, path, ownerToken, body, type);
    }

    /** Generic exchange that accepts a raw token (null = anonymous). */
    protected <T> ResponseEntity<T> exchange(HttpMethod method, String path, String token, Object body, Class<T> type) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, h), type);
    }

    // ────────────────────────────────────────────────────────────────────
    // Envelope assertions
    // ────────────────────────────────────────────────────────────────────

    /** Asserts the standard {@link ApiResponse} envelope: success=true, data!=null. */
    @SuppressWarnings("unchecked")
    protected <T> T assertOk(ResponseEntity<ApiResponse<T>> resp) {
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("Expected 2xx but was " + resp.getStatusCode())
                .isTrue();
        ApiResponse<T> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).as("envelope.success").isTrue();
        assertThat(body.getData()).as("envelope.data").isNotNull();
        return body.getData();
    }

    /** Asserts the envelope: success=true, body.data may be null (e.g. delete). */
    @SuppressWarnings("unchecked")
    protected ApiResponse<Object> assertOkEnvelope(ResponseEntity<?> resp) {
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("Expected 2xx but was " + resp.getStatusCode())
                .isTrue();
        ApiResponse<Object> body = (ApiResponse<Object>) resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body.isSuccess()).as("envelope.success").isTrue();
        return body;
    }

    // ────────────────────────────────────────────────────────────────────
    // JSON helpers for endpoints that don't return ApiResponse<T>
    // ────────────────────────────────────────────────────────────────────

    protected JsonNode readJson(ResponseEntity<String> resp) throws Exception {
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("Expected 2xx but was " + resp.getStatusCode() + " body=" + resp.getBody())
                .isTrue();
        return objectMapper.readTree(resp.getBody());
    }

    /** GET as JSON envelope, with a token (null = anonymous). */
    protected ResponseEntity<JsonNode> getForJson(String path, String token) {
        return jsonExchange(HttpMethod.GET, path, token, null);
    }

    protected ResponseEntity<JsonNode> postForJson(String path, String token, Object body) {
        return jsonExchange(HttpMethod.POST, path, token, body);
    }

    protected ResponseEntity<JsonNode> putForJson(String path, String token, Object body) {
        return jsonExchange(HttpMethod.PUT, path, token, body);
    }

    protected ResponseEntity<JsonNode> patchForJson(String path, String token, Object body) {
        return jsonExchange(HttpMethod.PATCH, path, token, body);
    }

    protected ResponseEntity<JsonNode> deleteForJson(String path, String token) {
        return jsonExchange(HttpMethod.DELETE, path, token, null);
    }

    private ResponseEntity<JsonNode> jsonExchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        h.setAccept(java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON));
        if (token != null) h.setBearerAuth(token);
        return rest.exchange(path, method, new HttpEntity<>(body, h), JsonNode.class);
    }

    /** Convenience: read {@code data} from a standard {@code ApiResponse} envelope. */
    protected JsonNode readData(ResponseEntity<JsonNode> resp) {
        assertThat(resp.getStatusCode().is2xxSuccessful())
                .as("Expected 2xx but was " + resp.getStatusCode() + " body=" + resp.getBody())
                .isTrue();
        JsonNode body = resp.getBody();
        assertThat(body).as("response body").isNotNull();
        // If it's an ApiResponse envelope, dig into data
        if (body.has("data")) {
            JsonNode data = body.get("data");
            // data may be null for some endpoints; caller checks
            return data;
        }
        return body;
    }

    protected <T> T exchangeAndGet(HttpMethod method, String path, String token, Object body, Class<T> type) {
        ResponseEntity<ApiResponse<T>> resp = exchange(method, path, token, body,
                (Class) org.springframework.core.ParameterizedTypeReference.forType(ApiResponse.class).getType());
        // Fallback to raw cast for callers that pass Class<ApiResponse<T>>; mostly used by getAs*
        return resp.getBody() != null ? resp.getBody().getData() : null;
    }

    // ────────────────────────────────────────────────────────────────────
    // User id shortcuts
    // ────────────────────────────────────────────────────────────────────

    protected UUID adminUserId() {
        return users.findByEmail("admin@osms.vn").orElseThrow().getId();
    }

    protected UUID ownerUserId() {
        return users.findByEmail("manager@osms.vn").orElseThrow().getId();
    }

    protected UUID operationsUserId() {
        return users.findByEmail("staff@osms.vn").orElseThrow().getId();
    }

    protected UUID salesUserId() {
        return users.findByEmail("viewer@osms.vn").orElseThrow().getId();
    }
}
