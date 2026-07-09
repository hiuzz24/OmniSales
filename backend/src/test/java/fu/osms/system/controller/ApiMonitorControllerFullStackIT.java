package fu.osms.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("API Monitor Controller — Full Stack IT")
class ApiMonitorControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("AM1 — GET /api/admin/monitor/summary as SYSTEM_ADMIN returns 200")
    void getSummary_asAdmin_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/admin/monitor/summary", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("AM2 — GET /api/admin/monitor/summary as OWNER returns 403")
    void getSummary_asOwner_returns403() {
        ResponseEntity<JsonNode> resp = getForJson("/api/admin/monitor/summary", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("AM3 — GET /api/admin/monitor/traffic?range=today returns 200")
    void getTraffic_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/admin/monitor/traffic?range=today", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("AM4 — GET /api/admin/monitor/endpoints returns 200")
    void getEndpoints_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/admin/monitor/endpoints", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}