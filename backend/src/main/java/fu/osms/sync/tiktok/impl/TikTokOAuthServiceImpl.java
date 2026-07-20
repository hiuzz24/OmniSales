package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.tiktok.TikTokOAuthService;
import fu.osms.sync.tiktok.TikTokApiClient;
import fu.osms.sync.tiktok.dto.TikTokAuthorizedShop;
import fu.osms.sync.tiktok.dto.TikTokTokenData;
import fu.osms.sync.tiktok.util.TikTokSignatureUtil;
import fu.osms.channel.token.dto.PlatformTokenRefreshResult;
import fu.osms.channel.token.exception.TokenRefreshException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TikTokOAuthServiceImpl implements TikTokOAuthService {

    @Value("${tiktok.app-key}")
    private String appKey;

    @Value("${tiktok.app-secret}")
    private String appSecret;

    @Value("${tiktok.token-url}")
    private String tokenUrl;

    @Value("${tiktok.refresh-token-url:https://auth.tiktok-shops.com/api/v2/token/refresh}")
    private String refreshTokenUrl;

    @Value("${tiktok.api-url:https://open-api.tiktokglobalshop.com}")
    private String apiUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final TikTokApiClient tikTokApiClient;

    @Override
    public TikTokTokenData exchangeToken(String code) {
        validateConfig();

        String fullUrl = UriComponentsBuilder.fromHttpUrl(tokenUrl)
                .queryParam("app_key", appKey)
                .queryParam("app_secret", appSecret)
                .queryParam("auth_code", code)
                .queryParam("grant_type", "authorized_code")
                .build()
                .encode()
                .toUriString();

        try {
            log.info("[TikTokOAuth] Exchanging authorization code for token");
            String responseStr = restTemplate.getForObject(fullUrl, String.class);
            Map<String, Object> response = objectMapper.readValue(responseStr, new TypeReference<>() {});
            TikTokTokenData tokenData = normalizeTokenResponse(response);
            enrichAuthorizedShop(tokenData);
            return tokenData;
        } catch (RestClientResponseException e) {
            log.error("[TikTokOAuth] Token exchange error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("TikTok token API returned error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("[TikTokOAuth] Failed to exchange token", e);
            throw new IllegalStateException("Failed to exchange TikTok token: " + e.getMessage(), e);
        }
    }

    @Override
    public TikTokTokenData exchangeTokenAndResolveShop(String code) {
        TikTokTokenData token = exchangeToken(code);
        List<TikTokAuthorizedShop> shops = tikTokApiClient.getAuthorizedShops(token.getAccessToken());
        log.info("[TikTokOAuth] Authorized shop count={}", shops.size());
        if (shops.size() != 1) {
            throw new IllegalStateException("TikTok authorization must contain exactly one active shop; received=" + shops.size());
        }
        TikTokAuthorizedShop shop = shops.get(0);
        Map<String, Object> metadata = new HashMap<>(token.getMetadata());
        metadata.put("shopCipher", shop.getShopCipher());
        putIfPresent(metadata, "shopId", shop.getShopId());
        putIfPresent(metadata, "shopName", shop.getShopName());
        putIfPresent(metadata, "region", shop.getRegion());
        return TikTokTokenData.builder()
                .accessToken(token.getAccessToken())
                .refreshToken(token.getRefreshToken())
                .expiresInSeconds(token.getExpiresInSeconds())
                .refreshExpiresInSeconds(token.getRefreshExpiresInSeconds())
                .accountId(token.getAccountId())
                .accountName(token.getAccountName())
                .metadata(metadata)
                .build();
    }

    @Override
    public PlatformTokenRefreshResult refreshToken(String refreshToken) {
        validateConfig();
        String fullUrl = UriComponentsBuilder.fromHttpUrl(refreshTokenUrl)
                .queryParam("app_key", appKey)
                .queryParam("app_secret", appSecret)
                .queryParam("refresh_token", refreshToken)
                .queryParam("grant_type", "refresh_token")
                .build()
                .encode()
                .toUriString();
        try {
            String responseStr = restTemplate.getForObject(fullUrl, String.class);
            Map<String, Object> response = objectMapper.readValue(responseStr, new TypeReference<>() {});
            TikTokTokenData token = normalizeTokenResponse(response);
            return PlatformTokenRefreshResult.builder()
                    .accessToken(token.getAccessToken())
                    .refreshToken(token.getRefreshToken())
                    .tokenExpiresAt(OffsetDateTime.now().plusSeconds(token.getExpiresInSeconds()))
                    .refreshTokenExpiresAt(token.getRefreshExpiresInSeconds() > 0
                            ? OffsetDateTime.now().plusSeconds(token.getRefreshExpiresInSeconds()) : null)
                    .grantedScopes(stringList(token.getMetadata().get("grantedScopes")))
                    .accountId(token.getAccountId())
                    .build();
        } catch (RestClientResponseException error) {
            String body = error.getResponseBodyAsString();
            String normalized = body == null ? "" : body.toLowerCase();
            if (normalized.contains("refresh_token")
                    && (normalized.contains("expired") || normalized.contains("invalid"))) {
                throw TokenRefreshException.expired("TikTok refresh token is invalid or expired; reconnect the channel");
            }
            throw TokenRefreshException.transientFailure("Unable to refresh TikTok token", error);
        } catch (TokenRefreshException error) {
            throw error;
        } catch (Exception error) {
            String normalized = error.getMessage() == null ? "" : error.getMessage().toLowerCase();
            if (normalized.contains("refresh")
                    && (normalized.contains("expired") || normalized.contains("invalid"))) {
                throw TokenRefreshException.expired(
                        "TikTok refresh token is invalid or expired; reconnect the channel");
            }
            throw TokenRefreshException.transientFailure("Unable to refresh TikTok token", error);
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichAuthorizedShop(TikTokTokenData tokenData) {
        try {
            String path = "/authorization/202309/shops";
            Map<String, Object> query = new TreeMap<>();
            query.put("app_key", appKey);
            query.put("timestamp", Instant.now().getEpochSecond());
            query.put("sign", TikTokSignatureUtil.sign(path, query, null, appSecret));

            UriComponentsBuilder uri = UriComponentsBuilder.fromHttpUrl(apiUrl).path(path);
            query.forEach(uri::queryParam);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-tts-access-token", tokenData.getAccessToken());
            String responseBody = restTemplate.exchange(
                    uri.build().encode().toUri(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            ).getBody();
            Map<String, Object> response = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Map<String, Object> data = response.get("data") instanceof Map<?, ?> value
                    ? (Map<String, Object>) value
                    : Map.of();
            Object shopsValue = data.get("shops");
            if (!(shopsValue instanceof List<?> shops) || shops.isEmpty() || !(shops.get(0) instanceof Map<?, ?> rawShop)) {
                return;
            }
            Map<String, Object> shop = (Map<String, Object>) rawShop;
            putIfPresent(tokenData.getMetadata(), "shopCipher", firstNonNull(shop.get("cipher"), shop.get("shop_cipher")));
            putIfPresent(tokenData.getMetadata(), "shopId", firstNonNull(shop.get("id"), shop.get("shop_id")));
            putIfPresent(tokenData.getMetadata(), "shopName", firstNonNull(shop.get("name"), shop.get("shop_name")));
            putIfPresent(tokenData.getMetadata(), "shopRegion", shop.get("region"));
        } catch (Exception e) {
            log.warn("[TikTokOAuth] Connected, but Get Authorized Shops failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private TikTokTokenData normalizeTokenResponse(Map<String, Object> response) {
        Object code = response.get("code");
        if (code != null && !"0".equals(String.valueOf(code))) {
            Object message = firstNonNull(response.get("message"), response.get("msg"), response.get("error_msg"));
            throw new IllegalStateException("TikTok did not return access_token. code=" + code + ", message=" + message);
        }

        Object dataValue = response.get("data");
        if (!(dataValue instanceof Map<?, ?>)) {
            throw new IllegalStateException("TikTok token response is missing data");
        }

        Map<String, Object> data = new HashMap<>((Map<String, Object>) dataValue);
        Object accessToken = data.get("access_token");
        if (accessToken == null || String.valueOf(accessToken).isBlank()) {
            throw new IllegalStateException("TikTok token response is missing access_token");
        }

        String accountId = stringValue(firstNonNull(data.get("open_id")));
        String accountName = stringValue(firstNonNull(data.get("seller_name"), accountId));

        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "openId", data.get("open_id"));
        putIfPresent(metadata, "accountId", accountId);
        putIfPresent(metadata, "accountName", accountName);
        putIfPresent(metadata, "region", data.get("seller_base_region"));
        putIfPresent(metadata, "sellerBaseRegion", data.get("seller_base_region"));
        putIfPresent(metadata, "refreshTokenExpireIn", data.get("refresh_token_expire_in"));
        putIfPresent(metadata, "userType", data.get("user_type"));
        putIfPresent(metadata, "grantedScopes", data.get("granted_scopes"));
        putIfPresent(metadata, "shopCipher", firstNonNull(data.get("shop_cipher"), data.get("cipher")));
        putIfPresent(metadata, "shopId", data.get("shop_id"));

        return TikTokTokenData.builder()
                .accessToken(stringValue(accessToken))
                .refreshToken(stringValue(data.get("refresh_token")))
                .expiresInSeconds(secondsUntilEpoch(data.get("access_token_expire_in")))
                .refreshExpiresInSeconds(secondsUntilEpoch(data.get("refresh_token_expire_in")))
                .accountId(accountId)
                .accountName(accountName)
                .metadata(metadata)
                .build();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) return null;
        return values.stream().map(String::valueOf).toList();
    }

    private void validateConfig() {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(appSecret) || !StringUtils.hasText(tokenUrl)) {
            throw new IllegalStateException("Missing TikTok OAuth configuration");
        }
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            metadata.put(key, value);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int secondsUntilEpoch(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            long expiresAtEpochSecond = value instanceof Number number
                    ? number.longValue()
                    : Long.parseLong(String.valueOf(value));
            long seconds = expiresAtEpochSecond - OffsetDateTime.now().toEpochSecond();
            if (seconds <= 0) {
                return 0;
            }
            return seconds > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) seconds;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
