package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.tiktok.TikTokApiClient;
import fu.osms.sync.tiktok.dto.TikTokAuthorizedShop;
import fu.osms.sync.tiktok.util.TikTokSignatureUtil;
import fu.osms.channel.token.exception.PlatformAccessTokenExpiredException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokApiClientImpl implements TikTokApiClient {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

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
        return executeRaw(apiPath, queryParams, null, accessToken, HttpMethod.GET);
    }

    @Override
    public String executePost(String apiPath,
                              Map<String, String> queryParams,
                              String rawJsonBody,
                              String accessToken) {
        return executeRaw(apiPath, queryParams, rawJsonBody, accessToken, HttpMethod.POST);
    }

    @Override
    public String executePut(String apiPath,
                             Map<String, String> queryParams,
                             String rawJsonBody,
                             String accessToken) {
        return executeRaw(apiPath, queryParams, rawJsonBody, accessToken, HttpMethod.PUT);
    }

    @Override
    public Map<String, Object> searchProducts(String accessToken, String shopCipher, String pageToken, OffsetDateTime changedSince) {
        validateShopCipher(shopCipher);
        String path = "/product/" + productApiVersion + "/products/search";
        Map<String, String> query = commonQuery(shopCipher);
        query.put("page_size", "100");
        if (pageToken != null && !pageToken.isBlank()) {
            query.put("page_token", pageToken);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ALL");
        if (changedSince != null) {
            body.put("update_time_ge", changedSince.toEpochSecond());
        }
        log.info("[TikTokApiClient] Product search request path={}, pageToken={}, body={}",
                path,
                pageToken == null || pageToken.isBlank() ? "<none>" : "<present>",
                body);
        return executeForMap(path, HttpMethod.POST, query, body, accessToken);
    }

    @Override
    public Map<String, Object> getProduct(String accessToken, String shopCipher, String productId) {
        validateShopCipher(shopCipher);
        String path = "/product/" + productDetailApiVersion + "/products/" + productId;
        return executeForMap(path, HttpMethod.GET, commonQuery(shopCipher), null, accessToken);
    }

    @Override
    public Map<String, Object> searchInventory(String accessToken,
                                               String shopCipher,
                                               List<String> productIds) {
        validateShopCipher(shopCipher);
        return executeForMap(
                "/product/202309/inventory/search",
                HttpMethod.POST,
                commonQuery(shopCipher),
                Map.of("product_ids", productIds),
                accessToken
        );
    }

    @Override
    public Map<String, Object> searchInventoryBySkuIds(String accessToken,
                                                       String shopCipher,
                                                       List<String> skuIds) {
        validateShopCipher(shopCipher);
        return executeForMap(
                "/product/202309/inventory/search",
                HttpMethod.POST,
                commonQuery(shopCipher),
                Map.of("sku_ids", skuIds),
                accessToken
        );
    }

    @Override
    public Map<String, Object> getWarehouses(String accessToken, String shopCipher) {
        validateShopCipher(shopCipher);
        return executeForMap(
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
        validateShopCipher(shopCipher);
        String path = "/product/202309/products/" + productId + "/inventory/update";
        Map<String, Object> response = executeForMap(
                path,
                HttpMethod.POST,
                commonQuery(shopCipher),
                Map.of("skus", skus),
                accessToken
        );
        Map<String, Object> data = asMap(response.get("data"));
        Object errors = data.get("errors");
        if (errors instanceof List<?> errorList && !errorList.isEmpty()) {
            throw new IllegalStateException("TikTok rejected inventory update: " + errors);
        }
    }

    @Override
    public String uploadProductImage(String imageUrl, String useCase, String accessToken) {
        validateConfiguration(accessToken);
        String path = "/product/202309/images/upload";
        String sourceImageUrl = requireHttpImageUrl(imageUrl, useCase);

        try {
            ResponseEntity<byte[]> source = restTemplate.getForEntity(sourceImageUrl, byte[].class);
            byte[] bytes = source.getBody();
            if (bytes == null || bytes.length == 0) {
                throw new IllegalStateException("TikTok source image is empty for " + useCase);
            }

            Map<String, String> params = signedParams(path, Map.of(), null);
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

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(apiUrl + path);
            params.forEach(builder::queryParam);
            log.info("[TikTokApiClient] Calling POST {}, params={}", path, params.keySet());
            ResponseEntity<String> response = restTemplate.exchange(
                    builder.build().encode().toUri(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            String responseBody = response.getBody();
            throwIfAccessTokenExpired(responseBody, null);
            return responseBody;
        } catch (RestClientResponseException e) {
            throw apiException("TikTok image upload failed", e);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("TikTok image upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<TikTokAuthorizedShop> getAuthorizedShops(String accessToken) {
        String response = executeGet("/authorization/202309/shops", Map.of(), accessToken);
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
            log.info("[TikTokApiClient] Authorized shop count={}", result.size());
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse TikTok authorized shops", e);
        }
    }

    private Map<String, Object> executeForMap(String path,
                                              HttpMethod method,
                                              Map<String, String> query,
                                              Object requestBody,
                                              String accessToken) {
        String rawBody = requestBody == null ? null : serialize(requestBody);
        String responseBody = executeRaw(path, query, rawBody, accessToken, method);
        try {
            Map<String, Object> response = objectMapper.readValue(responseBody, MAP_TYPE);
            ensureSuccess(response);
            return response;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot parse TikTok Shop API response", e);
        }
    }

    private String executeRaw(String apiPath,
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
                    new HttpEntity<>(rawJsonBody, headers),
                    String.class
            );
            return response.getBody();
        } catch (RestClientResponseException e) {
            throw apiException("TikTok Shop API error", e);
        } catch (PlatformAccessTokenExpiredException e) {
            throw e;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot call TikTok Shop API: " + e.getMessage(), e);
        }
    }

    private Map<String, String> signedParams(String apiPath,
                                             Map<String, String> queryParams,
                                             String rawJsonBody) {
        Map<String, String> params = new HashMap<>();
        if (queryParams != null) {
            params.putAll(queryParams);
        }
        params.put("app_key", appKey);
        params.put("timestamp", String.valueOf(Instant.now().getEpochSecond()));
        params.put("sign", TikTokSignatureUtil.sign(apiPath, params, rawJsonBody, appSecret));
        return params;
    }

    private Map<String, String> commonQuery(String shopCipher) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("shop_cipher", shopCipher);
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

    private void validateConfiguration(String accessToken) {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("Missing TIKTOK_APP_KEY or TIKTOK_APP_SECRET");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("TikTok channel has no access token; reconnect the channel");
        }
    }

    private void validateShopCipher(String shopCipher) {
        if (shopCipher == null || shopCipher.isBlank()) {
            throw new IllegalStateException("TikTok channel is missing shopCipher metadata");
        }
    }

    private String requireHttpImageUrl(String imageUrl, String useCase) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalStateException("TikTok " + useCase + " source image URL is missing");
        }
        String normalized = imageUrl.trim();
        try {
            URI uri = URI.create(normalized);
            if (uri.isAbsolute() && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()))) {
                return normalized;
            }
        } catch (IllegalArgumentException ignored) {
            // Converted to a domain-specific message below.
        }
        throw new IllegalStateException(
                "TikTok " + useCase + " source image must be an absolute http:// or https:// URL"
        );
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize TikTok Shop API request", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private RuntimeException apiException(String prefix, RestClientResponseException e) {
        throwIfAccessTokenExpired(e.getResponseBodyAsString(), e);
        return new IllegalStateException(
                prefix + " " + e.getStatusCode() + ": " + e.getResponseBodyAsString(),
                e
        );
    }

    private void throwIfAccessTokenExpired(String responseBody, Throwable cause) {
        if (responseBody == null || responseBody.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.path("code").asInt(-1) == 105002) {
                throw cause == null
                        ? new PlatformAccessTokenExpiredException("TikTok access token has expired")
                        : new PlatformAccessTokenExpiredException("TikTok access token has expired", cause);
            }
        } catch (PlatformAccessTokenExpiredException error) {
            throw error;
        } catch (Exception ignored) {
            // Preserve the original platform error when the response is not JSON.
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
