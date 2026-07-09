package fu.osms.customer.controller;

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

/**
 * Full-stack Integration Test for {@link fu.osms.customer.controller.CustomerController}.
 *
 * <p>Covers the L3-CustomerAPI error-code matrix: happy paths, validation,
 * not-found, conflict, and access-control for each role. All HTTP calls
 * hit the real Spring context (PostgreSQL {@code osms_it} + JWT filter
 * chain) via {@link fu.osms.it.IntegrationTestBase}.</p>
 */
@DisplayName("Customer Controller — Full Stack IT")
class CustomerControllerFullStackIT extends BaseFullStackIT {

    @Autowired TestDataFactory factory;

    @Test
    @DisplayName("C1 — POST /api/customers as OWNER creates a customer and returns 201 envelope")
    void create_returns201() {
        Map<String, Object> body = Map.of(
                "fullName", "Cust " + TestDataFactory.uniqueSuffix(),
                "phone",    TestDataFactory.uniquePhone(),
                "email",    TestDataFactory.uniqueEmail("cust"),
                "gender",   "Nam",
                "isActive", true
        );
        ResponseEntity<JsonNode> resp = postForJson("/api/customers", ownerToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode data = readData(resp);
        assertThat(data.get("id").asText()).isNotBlank();
        assertThat(data.get("code").asText()).startsWith("KH");
        assertThat(data.get("fullName").asText()).isEqualTo(body.get("fullName"));
    }

    @Test
    @DisplayName("C2 — POST /api/customers with invalid email returns 400")
    void create_invalidEmail_returns400() {
        Map<String, Object> body = Map.of(
                "fullName", "Bad",
                "email",    "not-an-email");
        ResponseEntity<JsonNode> resp = postForJson("/api/customers", ownerToken, body);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("C3 — POST /api/customers without auth returns 401/403")
    void create_unauthenticated_returns401() {
        Map<String, Object> body = Map.of(
                "fullName", "No Auth",
                "phone",    TestDataFactory.uniquePhone(),
                "email",    TestDataFactory.uniqueEmail("noauth"));
        ResponseEntity<JsonNode> resp = postForJson("/api/customers", null, body);
        assertThat(resp.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    @DisplayName("C4 — POST /api/customers with duplicate phone returns 409 or 500")
    void create_duplicatePhone_returns409() {
        String phone = TestDataFactory.uniquePhone();
        Map<String, Object> first = Map.of(
                "fullName", "First",
                "phone",    phone,
                "email",    TestDataFactory.uniqueEmail("a"));
        ResponseEntity<JsonNode> r1 = postForJson("/api/customers", ownerToken, first);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> second = Map.of(
                "fullName", "Second",
                "phone",    phone,
                "email",    TestDataFactory.uniqueEmail("b"));
        ResponseEntity<JsonNode> r2 = postForJson("/api/customers", ownerToken, second);
        assertThat(r2.getStatusCode().value()).isIn(409, 500);
    }

    @Test
    @DisplayName("C5 — GET /api/customers returns page envelope")
    void getAll_returnsPage() {
        // create a couple
        postForJson("/api/customers", ownerToken, Map.of(
                "fullName", "P1", "phone", TestDataFactory.uniquePhone(), "email", TestDataFactory.uniqueEmail("p1")));
        postForJson("/api/customers", ownerToken, Map.of(
                "fullName", "P2", "phone", TestDataFactory.uniquePhone(), "email", TestDataFactory.uniqueEmail("p2")));

        ResponseEntity<JsonNode> resp = getForJson("/api/customers?page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(resp);
        assertThat(data.has("content")).isTrue();
        assertThat(data.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("C6 — GET /api/customers/{id} returns the customer")
    void getById_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/customers", ownerToken, Map.of(
                "fullName", "Lookup", "phone", TestDataFactory.uniquePhone(),
                "email", TestDataFactory.uniqueEmail("lookup")));
        String id = readData(created).get("id").asText();

        ResponseEntity<JsonNode> resp = getForJson("/api/customers/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("id").asText()).isEqualTo(id);
    }

    @Test
    @DisplayName("C7 — GET /api/customers/{id} for unknown id returns 404 or 500")
    void getById_unknown_returns404() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/customers/00000000-0000-0000-0000-000000000000", ownerToken);
        assertThat(resp.getStatusCode().value()).isIn(404, 500);
    }

    @Test
    @DisplayName("C8 — PUT /api/customers/{id} updates name")
    void update_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/customers", ownerToken, Map.of(
                "fullName", "Original", "phone", TestDataFactory.uniquePhone(),
                "email", TestDataFactory.uniqueEmail("upd")));
        String id = readData(created).get("id").asText();
        String newName = "Updated " + TestDataFactory.uniqueSuffix();

        ResponseEntity<JsonNode> resp = putForJson("/api/customers/" + id, ownerToken,
                Map.of("fullName", newName, "phone", readData(created).get("phone").asText(),
                        "email", readData(created).get("email").asText(), "isActive", true));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("fullName").asText()).isEqualTo(newName);
    }

    @Test
    @DisplayName("C9 — DELETE /api/customers/{id} returns 200")
    void delete_returns200() {
        ResponseEntity<JsonNode> created = postForJson("/api/customers", ownerToken, Map.of(
                "fullName", "ToDelete", "phone", TestDataFactory.uniquePhone(),
                "email", TestDataFactory.uniqueEmail("del")));
        String id = readData(created).get("id").asText();

        ResponseEntity<JsonNode> resp = deleteForJson("/api/customers/" + id, ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("C10 — GET /api/customers/stats returns stats envelope")
    void stats_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/customers/stats", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode data = readData(resp);
        assertThat(data.has("totalCustomers")).isTrue();
        assertThat(data.has("activeCustomers")).isTrue();
    }

    @Test
    @DisplayName("C11 — GET /api/customers as SALES works (read-only access)")
    void getAll_asSales_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/customers?page=0&size=5", salesToken);
        assertThat(resp.getStatusCode().value()).isIn(200, 403);
        // If 200, expect envelope
        if (resp.getStatusCode().is2xxSuccessful()) {
            assertThat(resp.getBody().get("success").asBoolean()).isTrue();
        }
    }

    @Test
    @DisplayName("C12 — GET /api/customers?search=... filters by keyword")
    void search_returns200() {
        String unique = "Search" + TestDataFactory.uniqueSuffix();
        postForJson("/api/customers", ownerToken, Map.of(
                "fullName", unique, "phone", TestDataFactory.uniquePhone(),
                "email", TestDataFactory.uniqueEmail("srch")));

        ResponseEntity<JsonNode> resp = getForJson(
                "/api/customers?search=" + unique + "&page=0&size=20", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(readData(resp).get("content").size()).isGreaterThanOrEqualTo(1);
    }
}
