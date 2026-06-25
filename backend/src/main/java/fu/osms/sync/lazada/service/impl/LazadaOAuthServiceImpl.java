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

    private final LazadaApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public String buildAuthorizationUrl() {
        return UriComponentsBuilder.fromUriString(authUrl)
                .queryParam("response_type", "code")
                .queryParam("force_auth", "true")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("client_id", appKey)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public Map<String, Object> exchangeToken(String code) {
        Map<String, String> params = new HashMap<>();
        params.put("code", code);

        try {
            String responseStr = lazadaApiClient.executePost("/auth/token/create", params, null, null);
            log.info("[LazadaOAuth] Raw token response: {}", responseStr);
            Map<String, Object> responseMap = objectMapper.readValue(responseStr, new TypeReference<>() {});
            List<Map<String,Object>> countryUserInfo = (List<Map<String,Object>>) responseMap.get("country_user_info");
            if(countryUserInfo != null && !countryUserInfo.isEmpty()){
                Map<String,Object> sellerInfo = countryUserInfo.get(0);
                responseMap.putIfAbsent("account_id",sellerInfo.get("seller_id"));
                responseMap.putIfAbsent("account_name",sellerInfo.get("short_code"));
            }
            return responseMap;
        } catch (Exception e) {
            log.error("[LazadaOAuth] Failed to exchange token", e);
            throw new RuntimeException("Failed to exchange Lazada token", e);
        }
    }

}
