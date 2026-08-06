package fu.osms.address.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.address.entity.Country;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestCountriesServiceImpl Tests")
class RestCountriesServiceImplTest {

    @Mock private RestTemplate restTemplate;

    private RestCountriesServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new RestCountriesServiceImpl(restTemplate, objectMapper);
        ReflectionTestUtils.setField(service, "apiKey", "test-key");
        // invalidate cache by reflection
        ReflectionTestUtils.setField(service, "cachedCountries", null);
    }

    @Test
    @DisplayName("getAllCountries: returns empty list when API throws")
    void getAllCountries_apiError() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RuntimeException("timeout"));

        List<Country> result = service.getAllCountries();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllCountries: parses a single page and stops when more=false")
    void getAllCountries_singlePage() {
        String body = """
            {
              "data": {
                "objects": [
                  {"names": {"common": "Vietnam"}, "codes": {"alpha_3": "VNM"}, "flag": {"emoji": "\\ud83c\\uddfb\\ud83c\\uddf3"}},
                  {"names": {"common": "Thailand"}, "codes": {"alpha_3": "THA"}, "flag": {"emoji": "\\ud83c\\uddf9\\ud83c\\udded"}}
                ],
                "meta": {"more": false}
              }
            }
            """;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<Country> result = service.getAllCountries();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Country::getCode).containsExactly("THA", "VNM"); // alphabetical
        assertThat(result.get(0).getFlagEmoji()).isNotEmpty();
    }

    @Test
    @DisplayName("getAllCountries: skips entries with missing name or code")
    void getAllCountries_skipsIncomplete() {
        String body = """
            {
              "data": {
                "objects": [
                  {"names": {"common": "Vietnam"}, "codes": {"alpha_3": "VNM"}, "flag": {"emoji": "\\ud83c\\uddfb\\ud83c\\uddf3"}},
                  {"names": {"common": ""}, "codes": {"alpha_3": "ZZZ"}},
                  {"names": {"common": "NoCode"}},
                  {"codes": {"alpha_3": "NON"}}
                ],
                "meta": {"more": false}
              }
            }
            """;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<Country> result = service.getAllCountries();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getCode()).isEqualTo("VNM");
    }

    @Test
    @DisplayName("getAllCountries: returns empty list when API returns empty body")
    void getAllCountries_emptyBody() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(""));

        List<Country> result = service.getAllCountries();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getAllCountries: caches the result and reuses it on subsequent calls (within TTL)")
    void getAllCountries_caches() {
        String body = """
            {
              "data": {
                "objects": [
                  {"names": {"common": "Vietnam"}, "codes": {"alpha_3": "VNM"}, "flag": {"emoji": ""}}
                ],
                "meta": {"more": false}
              }
            }
            """;
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));

        List<Country> first = service.getAllCountries();
        List<Country> second = service.getAllCountries();

        assertThat(first).hasSize(1);
        assertThat(second).isSameAs(first); // cache hit
        verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));
    }
}
