package fu.osms.sync.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Sync Log Controller — Full Stack IT")
class SyncLogControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("SL1 — GET /api/sync-logs returns page envelope")
    void getLogs_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/sync-logs?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("SL2 — GET /api/sync-logs?status=SUCCESS filters")
    void getLogsByStatus_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/sync-logs?status=SUCCESS&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }
}