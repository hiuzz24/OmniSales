package fu.osms.sync.shopify.impl;

import fu.osms.sync.shopify.ShopifyOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyOAuthServiceImpl implements ShopifyOAuthService {

    @Value("${shopify.api-key}")
    private String apiKey;

    @Value("${shopify.api-secret}")
    private String apiSecret;

    @Value("${shopify.redirect-uri}")
    private String redirectUri;

    @Value("${shopify.scopes}")
    private String scopes;

    private final RestTemplate restTemplate;

    @Override
    public String buildAuthorizationUrl(String shop) {
        validateOAuthConfig();
        String normalizedShop = normalizeShop(shop);
        String state = UUID.randomUUID().toString();
        String requestedScopes = normalizeScopes(scopes);

        String authUrl = UriComponentsBuilder
                .fromUriString("https://" + normalizedShop + ".myshopify.com/admin/oauth/authorize")
                .queryParam("client_id", apiKey)
                .queryParam("scope", requestedScopes)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();

        log.info("[ShopifyOAuth] buildAuthorizationUrl - shop={}, redirectUri={}, scopes={}",
                normalizedShop, redirectUri, requestedScopes);
        return authUrl;
    }

    @Override
    public String exchangeCodeForToken(String shop, String code) {
        validateOAuthConfig();
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
            log.info("[ShopifyOAuth] Token exchange successful - shop={}, grantedScopes={}",
                    normalizedShop, response.get("scope"));
            return accessToken;
        } catch (Exception e) {
            log.error("[ShopifyOAuth] Token exchange failed - shop={}, error={}", normalizedShop, e.getMessage());
            throw new RuntimeException("Failed to exchange Shopify authorization code: " + e.getMessage(), e);
        }
    }

    private String normalizeScopes(String scopes) {
        if (scopes == null || scopes.isBlank()) {
            return "";
        }
        return String.join(",",
                java.util.Arrays.stream(scopes.split(","))
                        .map(String::trim)
                        .filter(scope -> !scope.isBlank())
                        .toList());
    }

    private String normalizeShop(String shop) {
        if (!StringUtils.hasText(shop)) {
            throw new IllegalArgumentException("Shop domain must not be blank");
        }
        String normalized = shop.trim().toLowerCase(Locale.ROOT)
                .replaceFirst("^https?://", "");
        int pathStart = normalized.indexOf('/');
        if (pathStart >= 0) {
            normalized = normalized.substring(0, pathStart);
        }
        if (normalized.endsWith(".myshopify.com")) {
            normalized = normalized.substring(0, normalized.length() - ".myshopify.com".length());
        }
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("Shop domain must not be blank");
        }
        return normalized;
    }

    private void validateOAuthConfig() {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(apiSecret)
                || !StringUtils.hasText(redirectUri) || !StringUtils.hasText(scopes)) {
            throw new IllegalStateException("Missing Shopify OAuth configuration");
        }
    }
}
