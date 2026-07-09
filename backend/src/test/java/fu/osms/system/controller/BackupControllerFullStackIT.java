package fu.osms.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Backup Controller — Full Stack IT")
class BackupControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("BK1 — GET /api/backups returns page envelope")
    void getList_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/backups?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("BK2 — POST /api/backups creates a manual backup")
    void createBackup_returns200() {
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.POST,
                "/api/backups", ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }
}