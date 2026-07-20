package fu.osms.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("System Log Controller — Full Stack IT")
class SystemLogControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("SYL1 — GET /api/system-logs returns page envelope")
    void getLogs_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/system-logs?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("SYL2 — GET /api/system-logs?level=ERROR filters")
    void getLogsByLevel_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/system-logs?level=ERROR&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}