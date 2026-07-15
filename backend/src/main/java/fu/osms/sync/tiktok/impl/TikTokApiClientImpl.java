package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.tiktok.TikTokApiClient;
import fu.osms.sync.tiktok.util.TikTokSignatureUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class TikTokApiClientImpl implements TikTokApiClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Value("${tiktok.app-key}")
    private String appKey;

    @Value("${tiktok.app-secret}")
    private String appSecret;

    @Value("${tiktok.api-url:https://open-api.tiktokglobalshop.com}")
    private String apiUrl;

    @Value("${tiktok.product-api-version:202502}")
    private String productApiVersion;

    @Value("${tiktok.product-detail-api-version:202309}")
    private String productDetailApiVersion;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> searchProducts(String accessToken, String shopCipher, String pageToken) {
        String path = "/product/" + productApiVersion + "/products/search";
        Map<String, Object> query = commonQuery(shopCipher);
        query.put("page_size", 100);
        if (pageToken != null && !pageToken.isBlank()) {
            query.put("page_token", pageToken);
        }
        return execute(path, HttpMethod.POST, query, Map.of("status", "ALL"), accessToken);
    }

    @Override
    public Map<String, Object> getProduct(String accessToken, String shopCipher, String productId) {
        String path = "/product/" + productDetailApiVersion + "/products/" + productId;
        return execute(path, HttpMethod.GET, commonQuery(shopCipher), null, accessToken);
    }

    @Override
    public Map<String, Object> searchInventory(String accessToken, String shopCipher, List<String> productIds) {
        return execute(
                "/product/202309/inventory/search",
                HttpMethod.POST,
                commonQuery(shopCipher),
                Map.of("product_ids", productIds),
                accessToken
        );
    }

    @Override
    public Map<String, Object> getWarehouses(String accessToken, String shopCipher) {
        return execute(
                "/logistics/202309/warehouses",
                HttpMethod.GET,
                commonQuery(shopCipher),
                null,
                accessToken
        );
    }

    @Override
    public void updateInventory(String accessToken,
                                String shopCipher,
                                String productId,
                                List<Map<String, Object>> skus) {
        String path = "/product/202309/products/" + productId + "/inventory/update";
        Map<String, Object> response = execute(
                path,
                HttpMethod.POST,
                commonQuery(shopCipher),
                Map.of("skus", skus),
                accessToken
        );
        Map<String, Object> data = map(response.get("data"));
        Object errors = data.get("errors");
        if (errors instanceof List<?> errorList && !errorList.isEmpty()) {
            throw new IllegalStateException("TikTok rejected inventory update: " + errors);
        }
    }

    private Map<String, Object> execute(String path,
                                        HttpMethod method,
                                        Map<String, Object> query,
                                        Object requestBody,
                                        String accessToken) {
        validateConfiguration(accessToken, query.get("shop_cipher"));
        try {
            String body = requestBody == null ? null : objectMapper.writeValueAsString(requestBody);
            query.put("sign", TikTokSignatureUtil.sign(path, query, body, appSecret));

            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(apiUrl).path(path);
            query.forEach(uriBuilder::queryParam);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-tts-access-token", accessToken);
            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            String responseBody = restTemplate.exchange(
                    uriBuilder.build().encode().toUri(),
                    method,
                    entity,
                    String.class
            ).getBody();
            Map<String, Object> response = objectMapper.readValue(responseBody, MAP_TYPE);
            ensureSuccess(response);
            return response;
        } catch (RestClientResponseException e) {
            throw new IllegalStateException(
                    "TikTok Shop API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
                    e
            );
        } catch (Exception e) {
            if (e instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("Cannot call TikTok Shop API: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> commonQuery(String shopCipher) {
        Map<String, Object> query = new TreeMap<>();
        query.put("app_key", appKey);
        query.put("shop_cipher", shopCipher);
        query.put("timestamp", Instant.now().getEpochSecond());
        return query;
    }

    private void ensureSuccess(Map<String, Object> response) {
        Object code = response.get("code");
        if (code != null && !"0".equals(String.valueOf(code))) {
            throw new IllegalStateException(
                    "TikTok Shop API failed: code=" + code + ", message=" + response.get("message")
            );
        }
    }

    private void validateConfiguration(String accessToken, Object shopCipher) {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("Missing TIKTOK_APP_KEY or TIKTOK_APP_SECRET");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("TikTok channel has no access token; reconnect the channel");
        }
        if (shopCipher == null || shopCipher.toString().isBlank()) {
            throw new IllegalStateException("TikTok channel is missing shopCipher metadata");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source
                ? new LinkedHashMap<>((Map<String, Object>) source)
                : new LinkedHashMap<>();
    }
}
