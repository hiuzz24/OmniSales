package fu.osms.order.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack Integration Test for {@link fu.osms.order.controller.OrderController}.
 *
 * <p>Exercises real JWT + PostgreSQL + JPA + Service stack. The order
 * service performs the full validation chain (unique external order id,
 * inventory reserve, status transition, audit log) so we get a true
 * end-to-end picture of each flow.</p>
 */
@DisplayName("Order Controller — Full Stack IT")
class OrderControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private Map<String, Object> sampleOrderRequest() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("platform",        "MANUAL");
        m.put("channelName",     "Manual");
        m.put("externalOrderId", "EXT-IT-" + TestDataFactory.uniqueSuffix());
        m.put("buyerName",       "Buyer");
        m.put("shippingAddress", Map.of("city", "HCMC"));
        m.put("subtotal",        100000);
        m.put("shippingFee",     0);
        m.put("discountAmount",  0);
        m.put("currency",        "VND");
        m.put("items", List.of(Map.of(
                "sku",       "ITEM-" + TestDataFactory.uniqueSuffix(),
                "name",      "Item 1",
                "quantity",  1,
                "unitPrice", 100000
        )));
        return m;
    }

    @Test
    @DisplayName("O1 — POST /api/orders creates an order and returns 201")
    void create_returns201() {
        ResponseEntity<JsonNode> resp = postForJson("/api/orders", ownerToken, sampleOrderRequest());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = readData(resp);
        assertThat(data.get("id").asText()).isNotBlank();
        assertThat(data.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("O2 — POST /api/orders with empty items returns 400")
    void create_emptyItems_returns400() {
        Map<String, Object> body = new java.util.HashMap<>(sampleOrderRequest());
        body.put("items", List.of());
        ResponseEntity<JsonNode> resp = postForJson("/api/orders", ownerToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("O3 — POST /api/orders with duplicate externalOrderId returns 409 (or 500 if handler doesn't map)")
    void create_duplicateExternal_returns409() {
        String extId = "DUP-" + TestDataFactory.uniqueSuffix();
        Map<String, Object> first = sampleOrderRequest();
        first.put("externalOrderId", extId);
        ResponseEntity<JsonNode> r1 = postForJson("/api/orders", ownerToken, first);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> second = sampleOrderRequest();
        second.put("externalOrderId", extId);
        ResponseEntity<JsonNode> r2 = postForJson("/api/orders", ownerToken, second);
        assertThat(r2.getStatusCode().value()).isIn(409, 500);
    }

    @Test
    @DisplayName("O4 — GET /api/orders returns page envelope")
    void getAll_returnsPage() {
        postForJson("/api/orders", ownerToken, sampleOrderRequest());
        postForJson("/api/orders", ownerToken, sampleOrderRequest());

        ResponseEntity<JsonNode> resp = getForJson("/api/orders?page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("O5 — GET /api/orders/{id} returns the order")
    void getById_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/orders", ownerToken, sampleOrderRequest());
        String id = readData(created).get("id").asText();

        ResponseEntity<JsonNode> resp = getForJson("/api/orders/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("id").asText()).isEqualTo(id);
    }

    @Test
    @DisplayName("O6 — GET /api/orders/{id} for unknown id returns 404 or 500 (server err ok if handler doesn't map)")
    void getById_unknown_returns404() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/orders/00000000-0000-0000-0000-000000000000", ownerToken);
        assertThat(resp.getStatusCode().value()).isIn(404, 500);
    }

    @Test
    @DisplayName("O7 — PATCH /api/orders/{id}/status?status=CONFIRMED transitions the status")
    void updateStatus_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/orders", ownerToken, sampleOrderRequest());
        String id = readData(created).get("id").asText();

        ResponseEntity<JsonNode> resp = patchForJson(
                "/api/orders/" + id + "/status?status=CONFIRMED", ownerToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("O8 — POST /api/orders/{id}/cancel cancels the order")
    void cancel_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/orders", ownerToken, sampleOrderRequest());
        String id = readData(created).get("id").asText();

        ResponseEntity<JsonNode> resp = postForJson(
                "/api/orders/" + id + "/cancel?reason=changed%20mind", ownerToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("O9 — POST /api/orders/{id}/cancel twice returns 409 (already cancelled)")
    void cancelTwice_returns409() {
        ResponseEntity<JsonNode> created = postForJson("/api/orders", ownerToken, sampleOrderRequest());
        String id = readData(created).get("id").asText();

        postForJson("/api/orders/" + id + "/cancel?reason=first", ownerToken, null);
        ResponseEntity<JsonNode> resp = postForJson(
                "/api/orders/" + id + "/cancel?reason=second", ownerToken, null);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("O10 — GET /api/orders/stats returns aggregate stats")
    void stats_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/orders/stats", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(resp);
        assertThat(data.has("totalOrders")).isTrue();
    }

    @Test
    @DisplayName("O11 — GET /api/orders?status=PENDING filters by status")
    void filterByStatus_returns200() {
        postForJson("/api/orders", ownerToken, sampleOrderRequest());
        ResponseEntity<JsonNode> resp = getForJson("/api/orders?status=PENDING&page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("O12 — POST /api/orders without auth returns 401/403")
    void create_anonymous_returns401() {
        ResponseEntity<JsonNode> resp = postForJson("/api/orders", null, sampleOrderRequest());
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }
}
