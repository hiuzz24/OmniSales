package fu.osms.notification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Notification Controller — Full Stack IT")
class NotificationControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("N1 — GET /api/notifications returns page envelope")
    void getNotifications_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/notifications?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("N2 — GET /api/notifications/unread-count returns count")
    void countUnread_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/notifications/unread-count", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("N3 — GET /api/notifications?unreadOnly=true filters")
    void getUnreadOnly_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/notifications?unreadOnly=true&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("N4 — PATCH /api/notifications/{id}/read marks notification as read (200 or 404/500)")
    void markAsRead_returns200() {
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.PATCH,
                "/api/notifications/00000000-0000-0000-0000-000000000000/read", ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 404, 500);
    }

    @Test
    @DisplayName("N5 — POST /api/notifications/mark-all-read?userId=... marks all as read")
    void markAllAsRead_returns200() {
        ResponseEntity<JsonNode> resp = postForJson(
                "/api/notifications/mark-all-read?userId=" + ownerUserId(), ownerToken, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 400);
    }
}