package fu.osms.address.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.address.service.RestCountriesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestCountriesServiceImpl implements RestCountriesService {

    private static final String BASE_URL = "https://api.restcountries.com/countries/v5";
    private static final int CACHE_TTL_MS = 60 * 60 * 1000;

    @Value("${app.rest-countries.api-key}")
    private String apiKey;

    private final RestTemplate restCountriesRestTemplate;
    private final ObjectMapper objectMapper;

    private List<fu.osms.address.entity.Country> cachedCountries = null;
    private long cacheTimestamp = 0;

    @Override
    public List<fu.osms.address.entity.Country> getAllCountries() {
        if (cachedCountries != null && System.currentTimeMillis() - cacheTimestamp < CACHE_TTL_MS) {
            return cachedCountries;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + apiKey);

            List<fu.osms.address.entity.Country> allCountries = new ArrayList<>();
            int offset = 0;
            int limit = 100;

            while (true) {
                String url = BASE_URL + "?region=Asia&limit=" + limit + "&offset=" + offset
                        + "&response_fields=names.common,codes.alpha_3,flag.emoji";
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restCountriesRestTemplate.exchange(
                        url, HttpMethod.GET, entity, String.class);

                String body = response.getBody();
                if (body == null || body.isBlank()) {
                    log.warn("REST Countries API returned empty body at offset {}", offset);
                    break;
                }

                JsonNode root = objectMapper.readTree(body);
                JsonNode objectsNode = root.path("data").path("objects");

                if (!objectsNode.isArray() || objectsNode.isEmpty()) {
                    break;
                }

                for (JsonNode country : objectsNode) {
                    String name = country.path("names").path("common").asText(null);
                    String code = country.path("codes").path("alpha_3").asText(null);
                    String flagEmoji = country.path("flag").path("emoji").asText(null);

                    if (name == null || name.isBlank() || code == null || code.isBlank()) {
                        continue;
                    }

                    allCountries.add(fu.osms.address.entity.Country.builder()
                            .code(code)
                            .name(name)
                            .flagEmoji(flagEmoji != null ? flagEmoji : "")
                            .build());
                }

                JsonNode meta = root.path("data").path("meta");
                boolean more = meta.path("more").asBoolean(false);
                if (!more) {
                    break;
                }

                offset += limit;
            }

            allCountries.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            cachedCountries = allCountries;
            cacheTimestamp = System.currentTimeMillis();

            log.info("Fetched {} countries from REST Countries API", allCountries.size());
            return allCountries;

        } catch (Exception e) {
            log.error("Failed to fetch countries from REST Countries API: {}", e.getMessage());
            return List.of();
        }
    }
}
