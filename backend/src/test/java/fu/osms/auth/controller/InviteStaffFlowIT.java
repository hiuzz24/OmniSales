package fu.osms.auth.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack integration tests for the Invite Staff flow
 * (POST /api/auth/invite-user, GET /api/auth/accept-invite/validate,
 * POST /api/auth/accept-invite, POST /api/users/{id}/cancel-invite).
 *
 * <p>Covers UAT scenarios SC-01 (Owner mời nhân viên) and SC-16
 * (Người được mời kích hoạt tài khoản qua link trong email).</p>
 */
@DisplayName("Invite Staff Flow — Full Stack IT")
class InviteStaffFlowIT extends BaseFullStackIT {

    private static final String INVITE_PATH = "/api/auth/invite-user";
    private static final String VALIDATE_PATH = "/api/auth/accept-invite/validate";
    private static final String ACCEPT_PATH = "/api/auth/accept-invite";

    private String uniqueInviteEmail() {
        return "invitee+" + UUID.randomUUID().toString().substring(0, 8) + "@test.osms.vn";
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        h.setBearerAuth(ownerToken);
        return h;
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        h.setBearerAuth(token);
        return h;
    }

    private ResponseEntity<JsonNode> postInvite(Map<String, String> body) {
        return rest.exchange(INVITE_PATH, HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders()), JsonNode.class);
    }

    private String latestTokenFor(String email) {
        return jdbc.queryForObject(
                "SELECT token FROM user_invite_tokens WHERE email = ? ORDER BY created_at DESC LIMIT 1",
                String.class, email);
    }

    private UUID latestUserIdFor(String email) {
        return jdbc.queryForObject(
                "SELECT id FROM users WHERE email = ? ORDER BY created_at DESC LIMIT 1",
                UUID.class, email);
    }

    @Test
    @DisplayName("INV-1 — Owner invites SALES staff → 200 + user INACTIVE in DB + token PENDING")
    void invite_newStaff_returns200_andCreatesInactiveUser() {
        String email = uniqueInviteEmail();
        ResponseEntity<JsonNode> resp = postInvite(Map.of(
                "email", email,
                "roleName", "SALES"
        ));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("success").asBoolean()).isTrue();

        String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE email = ?", String.class, email);
        String tokenStatus = jdbc.queryForObject(
                "SELECT status FROM user_invite_tokens WHERE email = ?", String.class, email);
        String tokenRole = jdbc.queryForObject(
                "SELECT role_name FROM user_invite_tokens WHERE email = ?", String.class, email);

        assertThat(status).isEqualTo("INACTIVE");
        assertThat(tokenStatus).isEqualTo("PENDING");
        assertThat(tokenRole).isEqualTo("SALES");
    }

    @Test
    @DisplayName("INV-2 — Missing email → 400 (validation)")
    void invite_missingEmail_returns400() {
        Map<String, String> body = new HashMap<>();
        body.put("roleName", "SALES");

        ResponseEntity<JsonNode> resp = postInvite(body);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("INV-3 — OWNER role rejected → 400")
    void invite_ownerRoleRejected() {
        ResponseEntity<JsonNode> resp = postInvite(Map.of(
                "email", uniqueInviteEmail(),
                "roleName", "OWNER"
        ));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("success").asBoolean()).isFalse();
        assertThat(resp.getBody().get("message").asText()).contains("Vai trò mời không hợp lệ");
    }

    @Test
    @DisplayName("INV-4 — Admin role rejected → 400")
    void invite_adminRoleRejected() {
        ResponseEntity<JsonNode> resp = postInvite(Map.of(
                "email", uniqueInviteEmail(),
                "roleName", "ADMIN"
        ));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("message").asText()).contains("Vai trò mời không hợp lệ");
    }

    @Test
    @DisplayName("INV-5 — Second invite to same email → 400 (pending)")
    void invite_secondInvitePendingRejected() {
        String email = uniqueInviteEmail();

        ResponseEntity<JsonNode> first = postInvite(Map.of("email", email, "roleName", "SALES"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> second = postInvite(Map.of("email", email, "roleName", "SALES"));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().get("message").asText())
                .contains("đang có một lời mời chưa xác nhận");
    }

    @Test
    @DisplayName("INV-6 — 'OPERATIONS STAFF' normalized to OPERATIONS")
    void invite_normalizeOperationsStaff() {
        String email = uniqueInviteEmail();
        ResponseEntity<JsonNode> resp = postInvite(Map.of(
                "email", email,
                "roleName", "OPERATIONS STAFF"
        ));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String tokenRole = jdbc.queryForObject(
                "SELECT role_name FROM user_invite_tokens WHERE email = ?", String.class, email);
        assertThat(tokenRole).isEqualTo("OPERATIONS");
    }

    @Test
    @DisplayName("INV-7 — GET /accept-invite/validate with valid token → 200 + email/roleName")
    void validateValidToken_returns200() {
        String email = uniqueInviteEmail();
        postInvite(Map.of("email", email, "roleName", "SALES"));
        String token = latestTokenFor(email);

        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<JsonNode> resp = rest.exchange(
                VALIDATE_PATH + "?token=" + token, HttpMethod.GET,
                new HttpEntity<>(h), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody().get("success").asBoolean()).isTrue();
        assertThat(resp.getBody().get("data").get("email").asText()).isEqualTo(email);
        assertThat(resp.getBody().get("data").get("roleName").asText()).isEqualTo("SALES");
    }

    @Test
    @DisplayName("INV-8 — GET /accept-invite/validate with unknown token → 400")
    void validateUnknownToken_returns400() {
        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<JsonNode> resp = rest.exchange(
                VALIDATE_PATH + "?token=does-not-exist", HttpMethod.GET,
                new HttpEntity<>(h), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("message").asText()).contains("Liên kết không hợp lệ");
    }

    @Test
    @DisplayName("INV-9 — GET /accept-invite/validate with expired token → 400")
    void validateExpiredToken_returns400() {
        String email = uniqueInviteEmail();
        postInvite(Map.of("email", email, "roleName", "SALES"));

        jdbc.update("UPDATE user_invite_tokens SET expires_at = NOW() - INTERVAL '1 minute' WHERE email = ?", email);
        String token = latestTokenFor(email);

        HttpHeaders h = new HttpHeaders();
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<JsonNode> resp = rest.exchange(
                VALIDATE_PATH + "?token=" + token, HttpMethod.GET,
                new HttpEntity<>(h), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("message").asText()).contains("đã hết hạn");
    }

    @Test
    @DisplayName("INV-10 — POST /accept-invite with mismatched passwords → 400")
    void acceptInvite_passwordMismatch_returns400() {
        String email = uniqueInviteEmail();
        postInvite(Map.of("email", email, "roleName", "SALES"));
        String token = latestTokenFor(email);

        ResponseEntity<JsonNode> resp = rest.exchange(ACCEPT_PATH, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "token", token,
                        "fullName", "New Staff",
                        "password", "Password123@",
                        "confirmPassword", "Different1!"
                ), jsonHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().get("message").asText()).contains("không khớp");
    }

    @Test
    @DisplayName("INV-11 — POST /accept-invite with weak password → 400")
    void acceptInvite_weakPassword_returns400() {
        String email = uniqueInviteEmail();
        postInvite(Map.of("email", email, "roleName", "SALES"));
        String token = latestTokenFor(email);

        // 12 chars but no uppercase / digit / special char — fails the
        // password @Pattern (which is what the test originally meant to
        // exercise; "không đúng định dạng").
        String weakPassword = "weakpassword";

        ResponseEntity<JsonNode> resp = rest.exchange(ACCEPT_PATH, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "token", token,
                        "fullName", "New Staff",
                        "password", weakPassword,
                        "confirmPassword", weakPassword
                ), jsonHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String message = resp.getBody().get("message").asText();
        JsonNode data = resp.getBody().path("data");
        boolean topLevelMismatch = message.contains("không đúng định dạng");
        boolean fieldLevelMismatch =
                !data.isMissingNode() && !data.isNull()
                        && data.toString().contains("không đúng định dạng");
        assertThat(topLevelMismatch || fieldLevelMismatch)
                .as("response should indicate a password format error")
                .isTrue();
    }

    @Test
    @DisplayName("INV-12 — POST /accept-invite happy path → user ACTIVE + login works with new password")
    void acceptInvite_happy_userActivatedAndLoginWorks() {
        String email = uniqueInviteEmail();
        postInvite(Map.of("email", email, "roleName", "SALES"));
        String token = latestTokenFor(email);
        String password = "Password123@";

        ResponseEntity<JsonNode> acceptResp = rest.exchange(ACCEPT_PATH, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "token", token,
                        "fullName", "  New Staff  ",
                        "password", password,
                        "confirmPassword", password
                ), jsonHeaders()), JsonNode.class);

        assertThat(acceptResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(acceptResp.getBody().get("success").asBoolean()).isTrue();

        String status = jdbc.queryForObject(
                "SELECT status FROM users WHERE email = ?", String.class, email);
        String fullName = jdbc.queryForObject(
                "SELECT full_name FROM users WHERE email = ?", String.class, email);
        String tokenStatus = jdbc.queryForObject(
                "SELECT status FROM user_invite_tokens WHERE email = ?", String.class, email);

        assertThat(status).isEqualTo("ACTIVE");
        assertThat(fullName).isEqualTo("New Staff");
        assertThat(tokenStatus).isEqualTo("ACCEPTED");

        // Newly created user should be able to login
        String newUserToken = login(email, password);
        assertThat(newUserToken).isNotBlank();
    }

    @Test
    @DisplayName("INV-13 — POST /accept-invite twice with same token → 400 (token used)")
    void acceptInvite_secondAttempt_returns400() {
        String email = uniqueInviteEmail();
        postInvite(Map.of("email", email, "roleName", "SALES"));
        String token = latestTokenFor(email);

        ResponseEntity<JsonNode> first = rest.exchange(ACCEPT_PATH, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "token", token,
                        "fullName", "First",
                        "password", "Password123@",
                        "confirmPassword", "Password123@"
                ), jsonHeaders()), JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> second = rest.exchange(ACCEPT_PATH, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "token", token,
                        "fullName", "Second",
                        "password", "Password123@",
                        "confirmPassword", "Password123@"
                ), jsonHeaders()), JsonNode.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().get("message").asText()).contains("đã được sử dụng");
    }

    @Test
    @DisplayName("INV-14 — Cancel invite via /api/users/{id}/cancel-invite removes user")
    void cancelInvite_removesUser() {
        String email = uniqueInviteEmail();
        postInvite(Map.of("email", email, "roleName", "SALES"));
        UUID userId = latestUserIdFor(email);

        ResponseEntity<JsonNode> resp = rest.exchange(
                "/api/users/" + userId + "/cancel-invite", HttpMethod.POST,
                new HttpEntity<>(jsonHeaders()), JsonNode.class);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ?", Integer.class, userId);
        assertThat(count).isZero();

        String tokenStatus = jdbc.queryForObject(
                "SELECT status FROM user_invite_tokens WHERE email = ?", String.class, email);
        assertThat(tokenStatus).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("INV-15 — Anonymous cannot invite (no token)")
    void invite_anonymousRejected() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<JsonNode> resp = rest.exchange(INVITE_PATH, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "email", uniqueInviteEmail(),
                        "roleName", "SALES"
                ), h), JsonNode.class);

        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }
}
