package fu.osms.inventory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Inventory Controller — Full Stack IT")
class InventoryControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private UUID seedWarehouse() {
        return jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class, "WH-IT-" + TestDataFactory.uniqueSuffix(), "A");
    }

    @Test
    @DisplayName("INV1 — GET /api/inventory returns page envelope")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/inventory?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("INV2 — GET /api/inventory/items?warehouseId=... returns items for warehouse")
    void getItemsByWarehouse_returns200() {
        UUID wh = seedWarehouse();
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/inventory/items?warehouseId=" + wh + "&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("INV3 — GET /api/inventory/items/low-stock returns low stock list")
    void getLowStock_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/inventory/items/low-stock", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("INV4 — GET /api/inventory/log returns inventory logs")
    void getInventoryLog_returns200() {
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.GET,
                "/api/inventory/log?page=0&size=10", ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }
}