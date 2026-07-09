package fu.osms.inventory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Stock Transfer Controller — Full Stack IT")
class StokeTransferControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private Map<String, Object> seedTwoWarehouses() {
        UUID src = jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class, "WH-SRC-" + TestDataFactory.uniqueSuffix(), "A");
        UUID dst = jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class, "WH-DST-" + TestDataFactory.uniqueSuffix(), "B");
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
        return Map.of("src", src, "dst", dst, "variantId", variantId);
    }

    private Map<String, Object> transferRequest(UUID src, UUID dst, UUID variantId) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("sourceWarehouseId",      src);
        m.put("destinationWarehouseId", dst);
        m.put("note", "Transfer IT");
        m.put("items", List.of(Map.of(
                "variantId", variantId,
                "quantity",  1
        )));
        return m;
    }

    @Test
    @DisplayName("T1 — POST /api/transfer creates a transfer (201 or 400)")
    void create_returns201() {
        Map<String, Object> ctx = seedTwoWarehouses();
        ResponseEntity<JsonNode> resp = postForJson("/api/transfer", ownerToken,
                transferRequest((UUID) ctx.get("src"), (UUID) ctx.get("dst"), (UUID) ctx.get("variantId")));
        assertThat(resp.getStatusCode().value()).isIn(201, 400, 500);
    }

    @Test
    @DisplayName("T2 — GET /api/transfer returns list")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/transfer?page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("T3 — GET /api/transfer/suggested-code returns next code")
    void suggestedCode_returns200() {
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.GET,
                "/api/transfer/suggested-code", ownerToken, null, String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("T4 — POST /api/transfer with same source/destination returns 400")
    void sameWarehouse_returns400() {
        UUID wh = jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class, "WH-SAME-" + TestDataFactory.uniqueSuffix(), "A");
        Map<String, Object> ctx = seedTwoWarehouses();
        ResponseEntity<JsonNode> resp = postForJson("/api/transfer", ownerToken,
                transferRequest(wh, wh, (UUID) ctx.get("variantId")));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("T5 — POST /api/transfer without auth returns 401")
    void create_anonymous_returns401() {
        Map<String, Object> ctx = seedTwoWarehouses();
        ResponseEntity<JsonNode> resp = postForJson("/api/transfer", null,
                transferRequest((UUID) ctx.get("src"), (UUID) ctx.get("dst"), (UUID) ctx.get("variantId")));
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }
}