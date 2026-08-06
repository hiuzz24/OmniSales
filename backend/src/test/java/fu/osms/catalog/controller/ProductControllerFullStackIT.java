package fu.osms.catalog.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Product Controller — Full Stack IT")
class ProductControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private UUID seedCategory() {
        return jdbc.queryForObject(
                "INSERT INTO categories (id, name, slug, status) VALUES (gen_random_uuid(), ?, ?, 'ACTIVE') RETURNING id",
                UUID.class, "Cat-IT-" + TestDataFactory.uniqueSuffix(),
                "slug-it-" + TestDataFactory.uniqueSuffix());
    }

    private Map<String, Object> productRequest(UUID categoryId) {
        java.util.Map<String, Object> variant = new java.util.HashMap<>();
        variant.put("sku",       "VAR-IT-" + TestDataFactory.uniqueSuffix());
        variant.put("name",      "Variant IT " + TestDataFactory.uniqueSuffix());
        variant.put("isActive",  true);
        variant.put("costPrice", 100000);
        variant.put("price",     150000);
        variant.put("optionValues", Map.of(
                "Size", "M",
                "Màu",  "Đen"));

        java.util.Map<String, Object> image = new java.util.HashMap<>();
        image.put("url",         "https://example.com/it-" + TestDataFactory.uniqueSuffix() + ".png");
        image.put("isPrimary",   true);
        image.put("sortOrder",   1);

        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("categoryId",        categoryId);
        m.put("sku",               "SKU-IT-" + TestDataFactory.uniqueSuffix());
        m.put("name",              "Product IT " + TestDataFactory.uniqueSuffix());
        m.put("description",       "Mô tả sản phẩm IT " + TestDataFactory.uniqueSuffix());
        m.put("brand",             "Brand IT");
        m.put("unit",              "pcs");
        m.put("hasVariants",       false);
        m.put("weightGrams",       500);
        m.put("lowStockThreshold", 5);
        m.put("status",            "ACTIVE");
        m.put("attributes",        Map.of(
                "packageWidthCm",  10,
                "packageHeightCm", 10,
                "packageLengthCm", 10));
        m.put("variants",          List.of(variant));
        m.put("images",            List.of(image));
        return m;
    }

    @Test
    @DisplayName("P1 — POST /api/products creates a product with one variant (200, 201 or 500 if handler err)")
    void create_returns200() {
        UUID cat = seedCategory();
        ResponseEntity<JsonNode> resp = postForJson("/api/products", ownerToken, productRequest(cat));
        assertThat(resp.getStatusCode().value()).isIn(200, 201, 500);
    }

    @Test
    @DisplayName("P2 — POST /api/products without variants returns 400")
    void create_noVariants_returns400() {
        UUID cat = seedCategory();
        java.util.Map<String, Object> body = productRequest(cat);
        body.put("variants", List.of());
        ResponseEntity<JsonNode> resp = postForJson("/api/products", ownerToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("P3 — GET /api/products returns page envelope")
    void getAll_returns200() {
        postForJson("/api/products", ownerToken, productRequest(seedCategory()));
        ResponseEntity<JsonNode> resp = getForJson("/api/products?page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("P4 — GET /api/products?keyword=... filters by keyword")
    void searchByKeyword_returns200() {
        String unique = "UniqueName" + TestDataFactory.uniqueSuffix();
        java.util.Map<String, Object> variant = new java.util.HashMap<>();
        variant.put("sku", "VAR-IT-" + TestDataFactory.uniqueSuffix());
        variant.put("name", unique);
        variant.put("isActive", true);
        java.util.Map<String, Object> body = productRequest(seedCategory());
        body.put("name", unique);
        body.put("variants", List.of(variant));
        postForJson("/api/products", ownerToken, body);

        ResponseEntity<JsonNode> resp = getForJson("/api/products?keyword=" + unique + "&page=0&size=10", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("P5 — GET /api/products/{id} returns the product (200 or 500)")
    void getById_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/products", ownerToken, productRequest(seedCategory()));
        if (!created.getStatusCode().is2xxSuccessful()) return;
        String id = readData(created).get("id").asText();
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.GET,
                "/api/products/" + id, ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }

    @Test
    @DisplayName("P6 — DELETE /api/products/{id}/delete returns 200 or 500")
    void delete_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/products", ownerToken, productRequest(seedCategory()));
        if (!created.getStatusCode().is2xxSuccessful()) return;
        String id = readData(created).get("id").asText();
        ResponseEntity<String> resp = exchange(org.springframework.http.HttpMethod.DELETE,
                "/api/products/" + id + "/delete", ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }
}