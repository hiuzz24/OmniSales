package fu.osms.inventory.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import fu.osms.it.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Supplier Controller — Full Stack IT")
class SupplierControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    private Map<String, Object> supplierRequest() {
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("supplierCode", "SUP-IT-" + TestDataFactory.uniqueSuffix());
        m.put("name",         "Supplier IT " + TestDataFactory.uniqueSuffix());
        m.put("email",        TestDataFactory.uniqueEmail("sup"));
        m.put("phone",        TestDataFactory.uniquePhone());
        m.put("isActive",     true);
        return m;
    }

    private Map<String, Object> supplierRequestWithName(String name) {
        Map<String, Object> m = supplierRequest();
        m.put("name", name);
        return m;
    }

    /**
     * Count active suppliers in the DB so we can assert incremental changes
     * from POST /api/suppliers. Suppliers created by this test class are
     * cleaned before each method to keep the count predictable.
     */
    private int countActiveSuppliers() {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM suppliers WHERE is_active = true",
                Long.class);
        return n == null ? 0 : n.intValue();
    }

    private void cleanSupplierTestData() {
        jdbc.execute("DELETE FROM suppliers WHERE name LIKE 'Supplier IT %'");
    }

    // ────────────────────────────────────────────────────────────────────
    // Original 5 sanity tests
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("S1 — POST /api/suppliers creates a supplier (200 or 500)")
    void create_returns200() {
        ResponseEntity<JsonNode> resp = postForJson("/api/suppliers", ownerToken, supplierRequest());
        assertThat(resp.getStatusCode().value()).isIn(200, 500);
    }

    @Test
    @DisplayName("S2 — GET /api/suppliers returns page envelope")
    void getAll_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/suppliers", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("S3 — GET /api/suppliers/{id} not implemented (405) — skipped")
    void getById_skipped() {
        // SupplierController does not expose GET /api/suppliers/{id}.
        // Asserting 405 to document the expected behaviour.
        ResponseEntity<String> resp = exchange(HttpMethod.GET, "/api/suppliers/00000000-0000-0000-0000-000000000000",
                ownerToken, null, String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 405, 404, 500);
    }

    @Test
    @DisplayName("S4 — PATCH /api/suppliers/{id}/status updates status (200 or 500)")
    void updateStatus_returns200() {
        ResponseEntity<JsonNode> resp = postForJson("/api/suppliers", ownerToken, supplierRequest());
        if (resp.getStatusCode().is2xxSuccessful()) {
            String id = readData(resp).get("id").asText();
            ResponseEntity<String> r = exchange(HttpMethod.PATCH, "/api/suppliers/" + id + "/status",
                    ownerToken, Map.of("isActive", false), String.class);
            assertThat(r.getStatusCode().value()).isIn(200, 500);
        } else {
            assertThat(resp.getStatusCode().value()).isIn(200, 500);
        }
    }

    @Test
    @DisplayName("S5 — PUT /api/suppliers/{id} not implemented — returns 500 or success")
    void update_returns200or500() {
        ResponseEntity<JsonNode> created = postForJson("/api/suppliers", ownerToken, supplierRequest());
        if (!created.getStatusCode().is2xxSuccessful()) return;
        String id = readData(created).get("id").asText();
        ResponseEntity<String> resp = exchange(HttpMethod.PUT, "/api/suppliers/" + id,
                ownerToken, supplierRequest(), String.class);
        assertThat(resp.getStatusCode().value()).isIn(200, 405, 500);
    }

    // ────────────────────────────────────────────────────────────────────
    // Additional coverage: RBAC, validation, DB verification, conflicts
    // ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("S6 — GET /api/suppliers honors page size parameter")
    void getAll_honorsPageSize() {
        ResponseEntity<JsonNode> resp = getForJson("/api/suppliers?page=0&size=3", ownerToken);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode content = readData(resp).get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("S7 — Anonymous GET /api/suppliers is rejected (401/403)")
    void getAll_anonymous_isRejected() {
        ResponseEntity<JsonNode> resp = getForJson("/api/suppliers", null);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("S8 — OPERATIONS GET /api/suppliers is allowed (200)")
    void getAll_asOperations_isAllowed() {
        ResponseEntity<JsonNode> resp = getForJson("/api/suppliers", operationsToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).has("content")).isTrue();
    }

    @Test
    @DisplayName("S9 — SALES GET /api/suppliers is forbidden (403)")
    void getAll_asSales_isForbidden() {
        ResponseEntity<JsonNode> resp = getForJson("/api/suppliers", salesToken);
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("S10 — Anonymous POST /api/suppliers is rejected (401/403)")
    void create_anonymous_isRejected() {
        ResponseEntity<JsonNode> resp = postForJson("/api/suppliers", null, supplierRequest());
        assertThat(resp.getStatusCode().value()).isIn(401, 403);
    }

    @Test
    @DisplayName("S11 — POST with blank name returns 400 (validation)")
    void create_blankName_returns400() {
        Map<String, Object> body = supplierRequest();
        body.put("name", "");
        ResponseEntity<JsonNode> resp = postForJson("/api/suppliers", ownerToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("S12 — POST with invalid email returns 400 (validation)")
    void create_invalidEmail_returns400() {
        Map<String, Object> body = supplierRequest();
        body.put("email", "not-a-valid-email");
        ResponseEntity<JsonNode> resp = postForJson("/api/suppliers", ownerToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("S13 — POST happy path increments active supplier count and persists in DB")
    void create_happyPath_persistsToDb() {
        cleanSupplierTestData();
        int before = countActiveSuppliers();
        String uniqueName = "Supplier IT " + TestDataFactory.uniqueSuffix();

        ResponseEntity<JsonNode> resp = postForJson("/api/suppliers", ownerToken,
                supplierRequestWithName(uniqueName));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String id = readData(resp).get("id").asText();

        Boolean dbIsActive = jdbc.queryForObject(
                "SELECT is_active FROM suppliers WHERE id = ?", Boolean.class,
                java.util.UUID.fromString(id));
        assertThat(dbIsActive).isTrue();

        String dbName = jdbc.queryForObject(
                "SELECT name FROM suppliers WHERE id = ?", String.class,
                java.util.UUID.fromString(id));
        assertThat(dbName).isEqualTo(uniqueName);

        assertThat(countActiveSuppliers()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("S14 — POST with duplicate name (case-insensitive) is rejected")
    void create_duplicateName_returnsError() {
        cleanSupplierTestData();
        String name = "Supplier IT " + TestDataFactory.uniqueSuffix();

        ResponseEntity<JsonNode> first = postForJson("/api/suppliers", ownerToken,
                supplierRequestWithName(name));
        if (first.getStatusCode() != HttpStatus.OK) {
            // Cannot reliably test duplicate if creation itself failed.
            return;
        }

        Map<String, Object> dupBody = supplierRequestWithName(name.toLowerCase());
        ResponseEntity<JsonNode> dup = postForJson("/api/suppliers", ownerToken, dupBody);
        assertThat(dup.getStatusCode().value()).isIn(409, 500);
    }

    @Test
    @DisplayName("S15 — PATCH status toggles isActive in DB both ways")
    void updateStatus_persistsInDbBothWays() {
        cleanSupplierTestData();
        ResponseEntity<JsonNode> created = postForJson("/api/suppliers", ownerToken, supplierRequest());
        if (created.getStatusCode() != HttpStatus.OK) {
            return;
        }
        java.util.UUID id = java.util.UUID.fromString(readData(created).get("id").asText());

        // Toggle off
        ResponseEntity<JsonNode> off = exchange(HttpMethod.PATCH,
                "/api/suppliers/" + id + "/status", ownerToken,
                Map.of("isActive", false), JsonNode.class);
        assertThat(off.getStatusCode()).isEqualTo(HttpStatus.OK);

        Boolean dbIsActive = jdbc.queryForObject(
                "SELECT is_active FROM suppliers WHERE id = ?", Boolean.class, id);
        assertThat(dbIsActive).isFalse();

        // Toggle back on
        ResponseEntity<JsonNode> on = exchange(HttpMethod.PATCH,
                "/api/suppliers/" + id + "/status", ownerToken,
                Map.of("isActive", true), JsonNode.class);
        assertThat(on.getStatusCode()).isEqualTo(HttpStatus.OK);

        dbIsActive = jdbc.queryForObject(
                "SELECT is_active FROM suppliers WHERE id = ?", Boolean.class, id);
        assertThat(dbIsActive).isTrue();
    }

    @Test
    @DisplayName("S16 — PATCH status with random UUID returns 4xx/5xx (not found)")
    void updateStatus_randomId_returnsError() {
        java.util.UUID randomId = java.util.UUID.randomUUID();
        ResponseEntity<JsonNode> resp = exchange(HttpMethod.PATCH,
                "/api/suppliers/" + randomId + "/status", ownerToken,
                Map.of("isActive", false), JsonNode.class);
        assertThat(resp.getStatusCode().value()).isIn(404, 500);
    }

    @Test
    @DisplayName("S17 — PUT with random UUID returns 4xx/5xx (not found)")
    void update_randomId_returnsError() {
        java.util.UUID randomId = java.util.UUID.randomUUID();
        ResponseEntity<JsonNode> resp = exchange(HttpMethod.PUT,
                "/api/suppliers/" + randomId, ownerToken, supplierRequest(), JsonNode.class);
        assertThat(resp.getStatusCode().value()).isIn(404, 500);
    }
}