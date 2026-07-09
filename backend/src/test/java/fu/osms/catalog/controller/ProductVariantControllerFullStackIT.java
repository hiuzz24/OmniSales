package fu.osms.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Product Variant Controller — Full Stack IT")
class ProductVariantControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("V1 — GET /api/catalog/variants returns page envelope")
    void search_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/catalog/variants?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("V2 — GET /api/catalog/variants?search=... filters")
    void searchByKeyword_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/catalog/variants?search=foo&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}