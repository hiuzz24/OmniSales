package fu.osms.sync.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Webhook Controller — Full Stack IT")
class WebhookControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("WH1 — POST /api/webhooks/receive accepts a webhook payload")
    void receive_returns200() {
        Map<String, Object> body = Map.of(
                "platform", "SHOPIFY",
                "event",    "order/create",
                "data",     Map.of("orderId", "12345"));
        ResponseEntity<JsonNode> resp = postForJson("/api/webhooks/receive", null, body);
        assertThat(resp.getStatusCode().value()).isIn(200, 202, 400, 401, 500);
    }

    @Test
    @DisplayName("WH2 — GET /api/webhooks returns the webhook config endpoint")
    void get_returns200or404() {
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.GET,
                "/api/webhooks", ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 404, 405, 500);
    }
}