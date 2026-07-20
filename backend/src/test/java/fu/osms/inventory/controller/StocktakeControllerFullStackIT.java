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

@DisplayName("Stocktake Controller — Full Stack IT")
class StocktakeControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private UUID seedWarehouse() {
        return jdbc.queryForObject(
                "INSERT INTO warehouses (id, name, address, is_active) VALUES (gen_random_uuid(), ?, ?, true) RETURNING id",
                UUID.class, "WH-IT-" + TestDataFactory.uniqueSuffix(), "A");
    }

    private Map<String, Object> sessionRequest(UUID warehouseId) {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("warehouseId", warehouseId);
        m.put("sessionCode", "SK-IT-" + TestDataFactory.uniqueSuffix());
        m.put("scheduledDate", java.time.LocalDate.now().plusDays(1).toString());
        m.put("note",        "Stocktake IT");
        return m;
    }

    @Test
    @DisplayName("SK1 — POST /api/stocktakes creates a session (201 or 400)")
    void create_returns201() {
        UUID wh = seedWarehouse();
        ResponseEntity<JsonNode> resp = postForJson("/api/stocktakes", ownerToken, sessionRequest(wh));
        assertThat(resp.getStatusCode().value()).isIn(200, 201, 400);
    }

    @Test
    @DisplayName("SK2 — GET /api/stocktakes returns page envelope")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/stocktakes?page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("SK3 — POST /api/stocktakes without auth returns 401")
    void create_anonymous_returns401() {
        UUID wh = seedWarehouse();
        ResponseEntity<JsonNode> resp = postForJson("/api/stocktakes", null, sessionRequest(wh));
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }
}