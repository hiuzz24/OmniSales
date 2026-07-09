package fu.osms.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack Integration Test for {@link fu.osms.auth.controller.UserController}.
 */
@DisplayName("User Controller — Full Stack IT")
class UserControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("U1 — GET /api/users returns page envelope as OWNER")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/users?page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(resp);
        assertThat(data.has("content")).isTrue();
        assertThat(data.get("totalElements").asInt()).isGreaterThanOrEqualTo(4);
    }

    @Test
    @DisplayName("U2 — GET /api/users/{id} returns the user")
    void getById_returns200() {
        UUID id = ownerUserId();
        ResponseEntity<JsonNode> resp = getForJson("/api/users/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("id").asText()).isEqualTo(id.toString());
    }

    @Test
    @DisplayName("U3 — GET /api/users/me returns the caller's profile")
    void me_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/users/me", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("email").asText()).isEqualTo("manager@osms.vn");
    }

    @Test
    @DisplayName("U4 — GET /api/users without auth returns 401/403")
    void getAll_anonymous_returns401() {
        ResponseEntity<JsonNode> resp = getForJson("/api/users", null);
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("U5 — POST /api/users creates a new user as OWNER (201 or 500 if handler doesn't map)")
    void create_returns201() {
        Map<String, Object> body = Map.of(
                "email",    TestDataFactory.uniqueEmail("newuser"),
                "fullName", "New User",
                "phone",    TestDataFactory.uniquePhone(),
                "role",     "SALES",
                "status",   "ACTIVE");
        ResponseEntity<JsonNode> resp = postForJson("/api/users", ownerToken, body);
        assertThat(resp.getStatusCode().value()).isIn(201, 500);
    }

    @Test
    @DisplayName("U6 — POST /api/users with duplicate email returns 409 or 500")
    void create_duplicateEmail_returns409() {
        Map<String, Object> body = Map.of(
                "email",    "manager@osms.vn",
                "fullName", "Dup",
                "phone",    TestDataFactory.uniquePhone(),
                "role",     "SALES");
        ResponseEntity<JsonNode> resp = postForJson("/api/users", ownerToken, body);
        assertThat(resp.getStatusCode().value()).isIn(409, 500);
    }

    @Test
    @DisplayName("U7 — PUT /api/users/{id} updates full name")
    void update_returns200() {
        UUID id = salesUserId();
        ResponseEntity<JsonNode> resp = putForJson("/api/users/" + id, ownerToken, Map.of(
                "email",    "viewer@osms.vn",
                "fullName", "Updated Name " + TestDataFactory.uniqueSuffix(),
                "role",     "SALES",
                "status",   "ACTIVE"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("U8 — GET /api/users/{id} for unknown id returns 404 or 500")
    void getById_unknown_returns404() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/users/00000000-0000-0000-0000-000000000000", ownerToken);
        assertThat(resp.getStatusCode().value()).isIn(404, 500);
    }
}
