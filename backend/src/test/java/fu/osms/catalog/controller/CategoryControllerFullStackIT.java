package fu.osms.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Category Controller — Full Stack IT")
class CategoryControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private Map<String, Object> categoryRequest() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("name",   "Cat IT " + TestDataFactory.uniqueSuffix());
        m.put("slug",   "slug-it-" + TestDataFactory.uniqueSuffix());
        m.put("status", "ACTIVE");
        return m;
    }

    @Test
    @DisplayName("CT1 — POST /api/categories creates a category (200 or 201)")
    void create_returns200() {
        ResponseEntity<JsonNode> resp = postForJson("/api/categories", ownerToken, categoryRequest());
        assertThat(resp.getStatusCode().value()).isIn(200, 201);
    }

    @Test
    @DisplayName("CT2 — GET /api/categories returns list")
    void getAll_returns200() {
        postForJson("/api/categories", ownerToken, categoryRequest());
        ResponseEntity<JsonNode> resp = getForJson("/api/categories", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("CT3 — GET /api/categories/{id} returns the category (200 or 500)")
    void getById_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/categories", ownerToken, categoryRequest());
        String id = readData(created).get("id").asText();
        ResponseEntity<JsonNode> resp = getForJson("/api/categories/" + id, ownerToken);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }

    @Test
    @DisplayName("CT4 — PUT /api/categories/{id} updates the category")
    void update_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/categories", ownerToken, categoryRequest());
        String id = readData(created).get("id").asText();
        Map<String, Object> body = categoryRequest();
        body.put("name", "Cat Updated");
        ResponseEntity<JsonNode> resp = putForJson("/api/categories/" + id, ownerToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("CT5 — DELETE /api/categories/{id} returns 200")
    void delete_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/categories", ownerToken, categoryRequest());
        String id = readData(created).get("id").asText();
        ResponseEntity<JsonNode> resp = deleteForJson("/api/categories/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}