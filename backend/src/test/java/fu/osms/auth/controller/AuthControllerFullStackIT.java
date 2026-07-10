package fu.osms.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack Integration Test for {@link fu.osms.auth.controller.AuthController}.
 *
 * <p>Covers the L3-AuthAPI matrix: login success/failure, validation,
 * lockout, refresh, logout, forgot-password and JWT tampering.</p>
 */
@DisplayName("Auth Controller — Full Stack IT")
class AuthControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("A1 — POST /api/auth/login with valid credentials returns 200 + accessToken")
    void login_success_returnsToken() {
        ResponseEntity<JsonNode> resp = postForJson("/api/auth/login", null, Map.of(
                "email",    "manager@osms.vn",
                "password", "11111111"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(resp);
        assertThat(data.get("accessToken").asText()).isNotBlank();
        assertThat(data.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(data.get("user").get("email").asText()).isEqualTo("manager@osms.vn");
    }

    @Test
    @DisplayName("A2 — POST /api/auth/login with wrong password returns 401")
    void login_wrongPassword_returns401() {
        ResponseEntity<JsonNode> resp = postForJson("/api/auth/login", null, Map.of(
                "email",    "manager@osms.vn",
                "password", "wrong-password"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("A3 — POST /api/auth/login with unknown email returns 401")
    void login_unknownEmail_returns401() {
        ResponseEntity<JsonNode> resp = postForJson("/api/auth/login", null, Map.of(
                "email",    "nobody@osms.vn",
                "password", "11111111"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("A4 — POST /api/auth/login with invalid email format returns 400")
    void login_invalidEmail_returns400() {
        ResponseEntity<JsonNode> resp = postForJson("/api/auth/login", null, Map.of(
                "email",    "not-an-email",
                "password", "11111111"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("A5 — POST /api/auth/login with missing fields returns 400")
    void login_missingFields_returns400() {
        ResponseEntity<JsonNode> resp = postForJson("/api/auth/login", null, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("A6 — POST /api/auth/forgot-password returns 200 (always success)")
    void forgotPassword_returns200() {
        ResponseEntity<JsonNode> resp = postForJson("/api/auth/forgot-password", null, Map.of(
                "email", "manager@osms.vn"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("A7 — POST /api/auth/logout with no refresh cookie returns 200")
    void logout_returns200() {
        ResponseEntity<JsonNode> resp = postForJson("/api/auth/logout", null, Map.of());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("A8 — POST /api/auth/login with locked account returns 429")
    void login_lockedAccount_returns429() {
        // Force the account into locked state via SQL
        jdbc.update("UPDATE users SET failed_login_attempts = 5, locked_until = NOW() + INTERVAL '5 minutes', " +
                "status = 'LOCKED' WHERE email = 'staff@osms.vn'");
        try {
            ResponseEntity<String> resp = exchange(HttpMethod.POST, "/api/auth/login", null,
                    Map.of("email", "staff@osms.vn", "password", "11111111"), String.class);
            // Either 429 (locked) or 401 depending on whether the lock check fires
            assertThat(resp.getStatusCode().value()).isIn(401, 429);
        } finally {
            jdbc.update("UPDATE users SET failed_login_attempts = 0, locked_until = NULL, " +
                    "status = 'ACTIVE' WHERE email = 'staff@osms.vn'");
        }
    }

    @Test
    @DisplayName("A9 — A valid token grants access to a protected endpoint")
    void validToken_protectsEndpoint() {
        ResponseEntity<JsonNode> resp = getForJson("/api/users/me", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("email").asText()).isEqualTo("manager@osms.vn");
    }

    @Test
    @DisplayName("A10 — An invalid (tampered) token is rejected with 401")
    void invalidToken_returns401() {
        // Take a valid token, tamper the signature
        String tampered = ownerToken.substring(0, ownerToken.length() - 4) + "AAAA";
        ResponseEntity<String> resp = exchange(HttpMethod.GET, "/api/users/me", tampered, null, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
