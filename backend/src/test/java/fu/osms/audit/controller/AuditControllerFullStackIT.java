package fu.osms.audit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Audit Controller — Full Stack IT")
class AuditControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("A1 — GET /api/audit-logs returns page envelope")
    void getLogs_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/audit-logs?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("A2 — GET /api/audit-logs?action=CREATE filters")
    void getLogsByAction_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/audit-logs?action=CREATE&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("A3 — GET /api/audit-logs/entity/{type}/{id} returns entity logs")
    void getByEntity_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/audit-logs/entity/Order/00000000-0000-0000-0000-000000000000?page=0&size=10",
                ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("A4 — GET /api/audit-logs/actor/{id} returns actor logs")
    void getByActor_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/audit-logs/actor/" + ownerUserId() + "?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}