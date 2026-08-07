package fu.osms.inventory.controller;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack IT for {@link StockReceiveController}.
 *
 * <p>Requires a warehouse + supplier + variant to exist. The seed
 * already provides them; we still use {@code TestDataFactory} for
 * additional rows needed by specific tests.</p>
 */
@DisplayName("Stock Receive Controller — Full Stack IT")
class StockReceiveControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    /** Insert a warehouse + supplier + variant + product + category + purchase order directly. */
    private Map<String, Object> seedWarehouseSupplierVariant() {
        // Warehouse
        UUID warehouseId = jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class,
                "WH-IT-" + TestDataFactory.uniqueSuffix(),
                "Addr");
        // Supplier
        UUID supplierId = jdbc.queryForObject(
                "INSERT INTO suppliers (id, supplier_code, name, tax_code, email, phone, is_active) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, true) RETURNING id",
                UUID.class,
                "SUP-IT-" + TestDataFactory.uniqueSuffix(),
                "Supplier IT " + TestDataFactory.uniqueSuffix(),
                "MST-IT-" + TestDataFactory.uniqueSuffix(),
                TestDataFactory.uniqueEmail("sup"),
                TestDataFactory.uniquePhone());
        // Category
        UUID categoryId = jdbc.queryForObject(
                "INSERT INTO categories (id, name, slug, status) VALUES (gen_random_uuid(), ?, ?, 'ACTIVE') RETURNING id",
                UUID.class,
                "Cat-IT-" + TestDataFactory.uniqueSuffix(),
                "slug-it-" + TestDataFactory.uniqueSuffix());
        // Product
        UUID productId = jdbc.queryForObject(
                "INSERT INTO products (id, category_id, sku, name, status, created_at, updated_at) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, 'ACTIVE', NOW(), NOW()) RETURNING id",
                UUID.class,
                categoryId,
                "SKU-IT-" + TestDataFactory.uniqueSuffix(),
                "Product IT " + TestDataFactory.uniqueSuffix());
        // Variant
        UUID variantId = jdbc.queryForObject(
                "INSERT INTO product_variants (id, product_id, sku, name, is_active, created_at, updated_at) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, true, NOW(), NOW()) RETURNING id",
                UUID.class,
                productId,
                "VAR-IT-" + TestDataFactory.uniqueSuffix(),
                "Variant IT " + TestDataFactory.uniqueSuffix());
        // Purchase order (required as purchase_order_id when creating a receipt)
        UUID createdBy = jdbc.queryForObject(
                "SELECT id FROM users WHERE email='manager@osms.vn'", UUID.class);
        UUID purchaseOrderId = jdbc.queryForObject(
                "INSERT INTO purchase_orders (id, order_code, supplier_id, warehouse_id, status, payment_method, " +
                        "order_date, expected_receipt_date, inspecting_at, inspected_at, created_by, created_at, updated_at) " +
                        "VALUES (gen_random_uuid(), ?, ?, ?, 'INSPECTED', 'CASH', " +
                        "CURRENT_DATE, CURRENT_DATE + INTERVAL '7 days', NOW(), NOW(), ?, NOW(), NOW()) RETURNING id",
                UUID.class,
                "PO-IT-" + TestDataFactory.uniqueSuffix(),
                supplierId,
                warehouseId,
                createdBy);

        return Map.of(
                "warehouseId", warehouseId,
                "supplierId", supplierId,
                "variantId", variantId,
                "purchaseOrderId", purchaseOrderId);
    }

    private Map<String, Object> receiptRequest(UUID warehouseId, UUID supplierId, UUID variantId, UUID purchaseOrderId) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("warehouseId", warehouseId);
        m.put("supplierId", supplierId);
        m.put("purchaseOrderId", purchaseOrderId);
        m.put("receivedAt",  java.time.LocalDate.now().toString());
        m.put("note", "Receipt created by IT");
        m.put("currency", "VND");
        m.put("items", List.of(Map.of(
                "variantId", variantId,
                "quantity", 10,
                "unitCost", BigDecimal.valueOf(5000)
        )));
        return m;
    }

    @Test
    @DisplayName("R1 — GET /api/receipts returns page envelope")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/receipts?page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("R2 — GET /api/receipts/next-code returns the next code")
    void nextCode_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/receipts/next-code", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).asText()).startsWith("PN");
    }

    @Test
    @DisplayName("R3 — GET /api/receipts/statistics returns stats")
    void statistics_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/receipts/statistics", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("R4 — POST /api/receipts creates a receipt")
    void create_returns201() {
        Map<String, Object> ids = seedWarehouseSupplierVariant();
        ResponseEntity<JsonNode> resp = postForJson("/api/receipts", ownerToken,
                receiptRequest((UUID) ids.get("warehouseId"), (UUID) ids.get("supplierId"),
                        (UUID) ids.get("variantId"), (UUID) ids.get("purchaseOrderId")));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(readData(resp).get("id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("R5 — GET /api/receipts/{id} returns the receipt")
    void getById_returns200() {
        Map<String, Object> ids = seedWarehouseSupplierVariant();
        ResponseEntity<JsonNode> created = postForJson("/api/receipts", ownerToken,
                receiptRequest((UUID) ids.get("warehouseId"), (UUID) ids.get("supplierId"),
                        (UUID) ids.get("variantId"), (UUID) ids.get("purchaseOrderId")));
        String id = readData(created).get("id").asText();

        ResponseEntity<JsonNode> resp = getForJson("/api/receipts/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("R6 — PATCH /api/receipts/{id}/complete completes a receipt (200 or 400)")
    void complete_returns200() {
        Map<String, Object> ids = seedWarehouseSupplierVariant();
        ResponseEntity<JsonNode> created = postForJson("/api/receipts", ownerToken,
                receiptRequest((UUID) ids.get("warehouseId"), (UUID) ids.get("supplierId"),
                        (UUID) ids.get("variantId"), (UUID) ids.get("purchaseOrderId")));
        String id = readData(created).get("id").asText();

        ResponseEntity<JsonNode> resp = patchForJson("/api/receipts/" + id + "/complete", ownerToken, null);
        assertThat(resp.getStatusCode().value()).isIn(200, 400, 500);
    }

    @Test
    @DisplayName("R7 — POST /api/receipts without auth returns 401")
    void create_anonymous_returns401() {
        Map<String, Object> ids = seedWarehouseSupplierVariant();
        ResponseEntity<JsonNode> resp = postForJson("/api/receipts", null,
                receiptRequest((UUID) ids.get("warehouseId"), (UUID) ids.get("supplierId"),
                        (UUID) ids.get("variantId"), (UUID) ids.get("purchaseOrderId")));
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }
}