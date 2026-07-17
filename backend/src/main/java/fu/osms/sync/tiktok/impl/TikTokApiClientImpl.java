package fu.osms.sync.tiktok.impl;

import fu.osms.sync.tiktok.TikTokApiClient;
import fu.osms.sync.tiktok.dto.TikTokAuthorizedShop;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.tiktok.TikTokApiClient;
import fu.osms.sync.tiktok.util.TikTokSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;

@Slf4j
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
    public String executeGet(String apiPath, Map<String, String> queryParams, String accessToken) {
        return execute(apiPath, queryParams, null, accessToken, HttpMethod.GET);
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
    public String executePost(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken) {
        return execute(apiPath, queryParams, rawJsonBody, accessToken, HttpMethod.POST);
    public Map<String, Object> getProduct(String accessToken, String shopCipher, String productId) {
        String path = "/product/" + productDetailApiVersion + "/products/" + productId;
        return execute(path, HttpMethod.GET, commonQuery(shopCipher), null, accessToken);
    }

    @Override
    public String executePut(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken) {
        return execute(apiPath, queryParams, rawJsonBody, accessToken, HttpMethod.PUT);
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
    public String uploadProductImage(String imageUrl, String useCase, String accessToken) {
        validateConfiguration(accessToken);
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
            ResponseEntity<byte[]> source = restTemplate.getForEntity(imageUrl, byte[].class);
            byte[] bytes = source.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("TikTok source image is empty: " + imageUrl);
            }

            Map<String, String> params = signedParams("/product/202309/images/upload", Map.of());
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-tts-access-token", accessToken);
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("data", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return "product-image.jpg";
                }
            });
            if (useCase != null && !useCase.isBlank()) {
                body.add("use_case", useCase);
            }

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl + "/product/202309/images/upload");
            params.forEach(builder::queryParam);
            ResponseEntity<String> response = restTemplate.exchange(
                    builder.build().encode().toUri(), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return response.getBody();
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("TikTok image upload failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("TikTok image upload failed: " + e.getMessage(), e);
        }
    }
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
    private String execute(String apiPath,
                           Map<String, String> queryParams,
                           String rawJsonBody,
                           String accessToken,
                           HttpMethod method) {
        validateConfiguration(accessToken);
        Map<String, String> params = signedParams(apiPath, queryParams, rawJsonBody);

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl + apiPath);
        params.forEach(builder::queryParam);
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-tts-access-token", accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            log.info("[TikTokApiClient] Calling {} {}, params={}", method, apiPath, params.keySet());
            ResponseEntity<String> response = restTemplate.exchange(
                    builder.build().encode().toUri(),
                    method,
                    entity,
                    new HttpEntity<>(rawJsonBody, headers),
                    String.class
            ).getBody();
            Map<String, Object> response = objectMapper.readValue(responseBody, MAP_TYPE);
            ensureSuccess(response);
            return response;
            );
            return response.getBody();
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
            log.error("[TikTokApiClient] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("TikTok API returned error: " + e.getResponseBodyAsString(), e);
        }
    }

    private void validateConfiguration(String accessToken, Object shopCipher) {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("Missing TIKTOK_APP_KEY or TIKTOK_APP_SECRET");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("TikTok channel has no access token; reconnect the channel");
        }
    }

    private Map<String, String> signedParams(String apiPath, Map<String, String> queryParams) {
        return signedParams(apiPath, queryParams, null);
        if (shopCipher == null || shopCipher.toString().isBlank()) {
            throw new IllegalStateException("TikTok channel is missing shopCipher metadata");
        }
    }

    private Map<String, String> signedParams(String apiPath, Map<String, String> queryParams, String rawJsonBody) {
        Map<String, String> params = new HashMap<>();
        if (queryParams != null) {
            params.putAll(queryParams);
        }
        params.put("app_key", appKey);
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        params.put("sign", sign(apiPath, params, rawJsonBody));
        return params;
    }

    @Override
    public List<TikTokAuthorizedShop> getAuthorizedShops(String accessToken) {
        String response = executeGet("/authorization/202309/shops", Map.of(), accessToken);
        log.info("[TikTokApiClient] Authorized shops response: {}", response);
        try {
            JsonNode shops = objectMapper.readTree(response).path("data").path("shops");
            List<TikTokAuthorizedShop> result = new ArrayList<>();
            if (shops.isArray()) {
                shops.forEach(shop -> {
                    String cipher = text(shop, "cipher");
                    if (cipher != null) {
                        result.add(TikTokAuthorizedShop.builder()
                                .shopCipher(cipher)
                                .shopId(text(shop, "id", "shop_id"))
                                .shopName(text(shop, "name", "shop_name"))
                                .region(text(shop, "region", "seller_base_region"))
                                .build());
                    }
                });
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse TikTok authorized shops", e);
        }
    }

    private String sign(String apiPath, Map<String, String> params, String rawJsonBody) {
        try {
            StringBuilder source = new StringBuilder(appSecret).append(apiPath);
            new TreeMap<>(params).forEach((key, value) -> {
                if (!"sign".equals(key) && !"access_token".equals(key) && value != null) {
                    source.append(key).append(value);
                }
            });
            if (rawJsonBody != null && !rawJsonBody.isBlank()) {
                source.append(rawJsonBody);
            }
            source.append(appSecret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(source.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign TikTok API request", e);
        }
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }
}
