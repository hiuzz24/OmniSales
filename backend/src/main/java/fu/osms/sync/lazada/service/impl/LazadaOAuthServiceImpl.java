package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.lazada.service.LazadaOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaOAuthServiceImpl implements LazadaOAuthService {

    @Value("${lazada.app-key}")
    private String appKey;

    @Value("${lazada.redirect-uri}")
    private String redirectUri;

    @Value("${lazada.auth-url}")
    private String authUrl;

    @Value("${lazada.auth-api-url:https://auth.lazada.com/rest}")
    private String authApiUrl;

    @Value("${lazada.api-url}")
    private String apiUrl;

    private final LazadaApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public String buildAuthorizationUrl() {
        if (!StringUtils.hasText(appKey) || !StringUtils.hasText(redirectUri) || !StringUtils.hasText(authUrl)) {
            throw new IllegalStateException("Thiếu cấu hình Lazada OAuth: LAZADA_APP_KEY hoặc LAZADA_REDIRECT_URI.");
        }

        String url = UriComponentsBuilder.fromUriString(authUrl)
                .queryParam("response_type", "code")
                .queryParam("force_auth", "true")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("client_id", appKey)
//                .queryParam("scope", "email")
                .build()
                .encode()
                .toUriString();
        log.info("[LazadaOAuth] authorize url={}", url);

        return url;
    }

    @Override
    public Map<String, Object> exchangeToken(String code) {
        Map<String, String> params = new HashMap<>();
        params.put("app_key", appKey);
        params.put("code", code);

        try {
            String responseStr = lazadaApiClient.executePost("/auth/token/create", params, null, null, apiUrl);
            log.info("[laz token response] {}", responseStr);
            Map<String, Object> responseMap = objectMapper.readValue(responseStr, new TypeReference<>() {});
            validateTokenResponse(responseMap);

            List<Map<String,Object>> countryUserInfo = (List<Map<String,Object>>) responseMap.get("country_user_info");
            if(countryUserInfo != null && !countryUserInfo.isEmpty()){
                Map<String,Object> sellerInfo = countryUserInfo.get(0);
                Object sellerId = firstNonNull(sellerInfo.get("seller_id"), sellerInfo.get("user_id"), sellerInfo.get("account_id"));
                Object sellerName = firstNonNull(sellerInfo.get("short_code"), sellerInfo.get("seller_name"), sellerInfo.get("name"), sellerId);
                if (sellerId != null) {
                    responseMap.putIfAbsent("account_id", String.valueOf(sellerId));
                }
                if (sellerName != null) {
                    responseMap.putIfAbsent("account_name", String.valueOf(sellerName));
                }
            }
            log.info("[LazadaOAuth] Token exchange succeeded");
            return responseMap;
        } catch (Exception e) {
            log.error("[LazadaOAuth] Failed to exchange token", e);
            throw new RuntimeException("Failed to exchange Lazada token", e);
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

    private void validateTokenResponse(Map<String, Object> responseMap) {
        Object code = responseMap.get("code");
        if (code != null && !"0".equals(String.valueOf(code))) {
            Object message = firstNonNull(responseMap.get("message"), responseMap.get("msg"), responseMap.get("error_msg"));
            throw new IllegalStateException("Lazada không trả access_token. code=" + code + ", message=" + message);
        }

        Object accessToken = responseMap.get("access_token");
        if (accessToken == null || String.valueOf(accessToken).isBlank()) {
            Object message = firstNonNull(responseMap.get("message"), responseMap.get("msg"), responseMap.get("error_msg"));
            throw new IllegalStateException("Lazada token response thiếu access_token. message=" + message);
        }
    }
}
