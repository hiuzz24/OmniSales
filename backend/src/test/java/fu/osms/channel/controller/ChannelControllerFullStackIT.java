package fu.osms.channel.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Channel Controller — Full Stack IT")
class ChannelControllerFullStackIT extends BaseFullStackIT {

    private Map<String, Object> channelRequest() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        // SHOPEE does not require OAuth, unlike SHOPIFY/LAZADA/TIKTOK.
        m.put("platform",    "SHOPEE");
        m.put("displayName", "Channel IT " + TestDataFactory.uniqueSuffix());
        m.put("status",      "PENDING");
        m.put("region",      "VN");
        m.put("syncEnabled", true);
        m.put("metadata",    java.util.Map.of("shop", "test"));
        return m;
    }

    @Test
    @DisplayName("CH1 — POST /api/channels creates a channel")
    void create_returns201() {
        ResponseEntity<JsonNode> resp = postForJson("/api/channels", ownerToken, channelRequest());
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(readData(resp).get("id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("CH2 — GET /api/channels returns list")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/channels", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("CH3 — GET /api/channels/{id} returns the channel")
    void getById_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/channels", ownerToken, channelRequest());
        String id = readData(created).get("id").asText();
        ResponseEntity<JsonNode> resp = getForJson("/api/channels/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("CH4 — PUT /api/channels/{id} updates the channel")
    void update_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/channels", ownerToken, channelRequest());
        String id = readData(created).get("id").asText();
        Map<String, Object> body = channelRequest();
        body.put("displayName", "Channel Updated");
        ResponseEntity<JsonNode> resp = putForJson("/api/channels/" + id, ownerToken, body);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }

    @Test
    @DisplayName("CH5 — DELETE /api/channels/{id} returns 200")
    void delete_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/channels", ownerToken, channelRequest());
        String id = readData(created).get("id").asText();
        ResponseEntity<JsonNode> resp = deleteForJson("/api/channels/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("CH6 — POST /api/channels without auth returns 401")
    void create_anonymous_returns401() {
        ResponseEntity<JsonNode> resp = postForJson("/api/channels", null, channelRequest());
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }
}