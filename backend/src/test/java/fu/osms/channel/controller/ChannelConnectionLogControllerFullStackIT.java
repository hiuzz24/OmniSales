package fu.osms.channel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Channel Connection Log Controller — Full Stack IT")
class ChannelConnectionLogControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("CCL1 — GET /api/channel-connection-logs returns page envelope")
    void getLogs_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/channel-connection-logs?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("CCL2 — GET /api/channel-connection-logs filters by platform")
    void getLogsByPlatform_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/channel-connection-logs?platform=SHOPIFY&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}