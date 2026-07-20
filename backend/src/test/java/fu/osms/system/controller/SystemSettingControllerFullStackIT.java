package fu.osms.system.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("System Setting Controller — Full Stack IT")
class SystemSettingControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("SS1 — GET /api/admin/settings returns list as SYSTEM_ADMIN")
    void getAllSettings_asAdmin_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/admin/settings", adminToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("SS2 — GET /api/admin/settings as OWNER returns 403")
    void getAllSettings_asOwner_returns403() {
        ResponseEntity<JsonNode> resp = getForJson("/api/admin/settings", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("SS3 — PUT /api/admin/settings/{key} updates a setting")
    void updateSetting_returns200() {
        // Get current settings to find a key
        ResponseEntity<JsonNode> list = getForJson("/api/admin/settings", adminToken);
        if (list.getBody() == null || !list.getBody().has("data")
                || list.getBody().get("data").size() == 0) {
            return;
        }
        String key = list.getBody().get("data").get(0).get("key").asText();
        ResponseEntity<JsonNode> resp = putForJson("/api/admin/settings/" + key, adminToken,
                java.util.Map.of("value", "IT-test-" + System.nanoTime()));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}