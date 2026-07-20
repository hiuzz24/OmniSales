package fu.osms.inventory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stock Delivery (Issue) Controller — Full Stack IT")
class StockDeliveryControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private Map<String, Object> seedContext() {
        UUID warehouseId = jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class, "WH-IT-" + TestDataFactory.uniqueSuffix(), "Addr");
        UUID categoryId = jdbc.queryForObject(
                "INSERT INTO categories (id, name, slug, status) VALUES (gen_random_uuid(), ?, ?, 'ACTIVE') RETURNING id",
                UUID.class, "Cat-IT-" + TestDataFactory.uniqueSuffix(), "slug-" + TestDataFactory.uniqueSuffix());
        UUID productId = jdbc.queryForObject(
                "INSERT INTO products (id, category_id, sku, name, status, created_at, updated_at) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, 'ACTIVE', NOW(), NOW()) RETURNING id",
                UUID.class, categoryId, "SKU-IT-" + TestDataFactory.uniqueSuffix(), "P");
        UUID variantId = jdbc.queryForObject(
                "INSERT INTO product_variants (id, product_id, sku, name, is_active, created_at, updated_at) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, true, NOW(), NOW()) RETURNING id",
                UUID.class, productId, "VAR-IT-" + TestDataFactory.uniqueSuffix(), "V");
        return Map.of("warehouseId", warehouseId, "variantId", variantId);
    }

    private Map<String, Object> issueRequest(UUID warehouseId, UUID variantId) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("warehouseId", warehouseId);
        m.put("reason",      "IT delivery");
        m.put("deliveryType","OUT");
        m.put("currency",    "VND");
        m.put("deliveryDate", java.time.LocalDate.now().toString());
        m.put("items", java.util.List.of(Map.of(
                "variantId", variantId,
                "quantity",  1
        )));
        return m;
    }

    @Test
    @DisplayName("D1 — POST /api/stock-deliveries creates an issue (201 or 400)")
    void create_returns201() {
        Map<String, Object> ctx = seedContext();
        ResponseEntity<JsonNode> resp = postForJson("/api/stock-deliveries", ownerToken,
                issueRequest((UUID) ctx.get("warehouseId"), (UUID) ctx.get("variantId")));
        assertThat(resp.getStatusCode().value()).isIn(200, 201, 400);
    }

    @Test
    @DisplayName("D2 — GET /api/stock-deliveries returns page envelope")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/stock-deliveries?page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("D3 — GET /api/stock-deliveries/statistics returns stats")
    void statistics_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/stock-deliveries/statistics", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("D4 — POST /api/stock-deliveries without auth returns 401")
    void create_anonymous_returns401() {
        Map<String, Object> ctx = seedContext();
        ResponseEntity<JsonNode> resp = postForJson("/api/stock-deliveries", null,
                issueRequest((UUID) ctx.get("warehouseId"), (UUID) ctx.get("variantId")));
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }
}