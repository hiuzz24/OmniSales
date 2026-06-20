package fu.osms.sync.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ShopifyOAuthService {

    @Value("${shopify.api-key}")
    private String apiKey;

    @Value("${shopify.api-secret}")
    private String apiSecret;

    @Value("${shopify.redirect-uri}")
    private String redirectUri;

    @Value("${shopify.scopes}")
    private String scopes;

    private final RestTemplate restTemplate;

    public ShopifyOAuthService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String buildAuthorizationUrl(String shop) {
        String normalizedShop = normalizeShop(shop);
        String state = UUID.randomUUID().toString();

        String authUrl = String.format(
                "https://%s.myshopify.com/admin/oauth/authorize?client_id=%s&scope=%s&redirect_uri=%s&state=%s",
                normalizedShop, apiKey, scopes, redirectUri, state
        );

        log.info("[ShopifyOAuth] buildAuthorizationUrl — shop={}, redirectUri={}", normalizedShop, redirectUri);
        return authUrl;
    }

    public String exchangeCodeForToken(String shop, String code) {
        String normalizedShop = normalizeShop(shop);
        String tokenUrl = String.format("https://%s.myshopify.com/admin/oauth/access_token", normalizedShop);

        log.info("[ShopifyOAuth] exchangeCodeForToken — shop={}", normalizedShop);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of(
                "client_id", apiKey,
                "client_secret", apiSecret,
                "code", code
        );

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            Map<String, Object> response = restTemplate.postForObject(tokenUrl, request, Map.class);

            if (response == null || !response.containsKey("access_token")) {
                throw new RuntimeException("Shopify did not return an access_token");
            }

            String accessToken = (String) response.get("access_token");
            log.info("[ShopifyOAuth] Token exchange successful — shop={}", normalizedShop);
            return accessToken;
        } catch (Exception e) {
            log.error("[ShopifyOAuth] Token exchange failed — shop={}, error={}", normalizedShop, e.getMessage());
            throw new RuntimeException("Failed to exchange Shopify authorization code: " + e.getMessage(), e);
        }
    }

    private String normalizeShop(String shop) {
        if (shop == null || shop.isBlank()) {
            throw new IllegalArgumentException("Shop domain must not be blank");
        }
        return shop.endsWith(".myshopify.com")
                ? shop.substring(0, shop.length() - ".myshopify.com".length())
                : shop;
    }
}
