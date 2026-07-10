package fu.osms.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Product Log Controller — Full Stack IT")
class ProductLogControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("PL1 — GET /api/product-logs returns page envelope")
    void getLogs_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/product-logs?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("PL2 — GET /api/product-logs?productId=... filters")
    void getLogsByProduct_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/product-logs?productId=00000000-0000-0000-0000-000000000000&page=0&size=10",
                ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}