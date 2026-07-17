package fu.osms.sync.tiktok.impl;

import fu.osms.sync.tiktok.TikTokApiClient;
import fu.osms.sync.tiktok.dto.TikTokAuthorizedShop;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.List;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokApiClientImpl implements TikTokApiClient {

    @Value("${tiktok.app-key}")
    private String appKey;

    @Value("${tiktok.app-secret}")
    private String appSecret;

    @Value("${tiktok.api-url:https://open-api.tiktokglobalshop.com}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public String executeGet(String apiPath, Map<String, String> queryParams, String accessToken) {
        return execute(apiPath, queryParams, null, accessToken, HttpMethod.GET);
    }

    @Override
    public String executePost(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken) {
        return execute(apiPath, queryParams, rawJsonBody, accessToken, HttpMethod.POST);
    }

    @Override
    public String executePut(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken) {
        return execute(apiPath, queryParams, rawJsonBody, accessToken, HttpMethod.PUT);
    }

    @Override
    public String uploadProductImage(String imageUrl, String useCase, String accessToken) {
        validateConfiguration(accessToken);
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
                    new HttpEntity<>(rawJsonBody, headers),
                    String.class
            );
            return response.getBody();
        } catch (RestClientResponseException e) {
            log.error("[TikTokApiClient] API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("TikTok API returned error: " + e.getResponseBodyAsString(), e);
        }
    }

    private void validateConfiguration(String accessToken) {
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("Missing TikTok API configuration");
        }
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("TikTok access token is missing");
        }
    }

    private Map<String, String> signedParams(String apiPath, Map<String, String> queryParams) {
        return signedParams(apiPath, queryParams, null);
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
