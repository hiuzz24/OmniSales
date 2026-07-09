package fu.osms.address.controller;

import com.fasterxml.jackson.databind.JsonNode;
import fu.osms.it.BaseFullStackIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Address Controller — Full Stack IT")
class AddressControllerFullStackIT extends BaseFullStackIT {

    @Test
    @DisplayName("AD1 — GET /api/address/countries returns list")
    void getCountries_returns200() {
        ResponseEntity<JsonNode> resp = getForJson("/api/address/countries", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("AD2 — GET /api/address/divisions returns divisions")
    void getDivisions_returns200() {
        ResponseEntity<JsonNode> resp = getForJson(
                "/api/address/divisions?country=VN&level=1", ownerToken);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}