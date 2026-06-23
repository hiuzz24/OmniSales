package fu.osms.sync.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.service.LazadaApiClient;
import fu.osms.sync.service.LazadaOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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
        return authUrl + "?response_type=code&force_auth=true&redirect_uri=" + redirectUri + "&client_id=" + appKey;
    }

    @Override
    public Map<String, Object> exchangeToken(String code) {
        Map<String, String> params = new HashMap<>();
        params.put("code", code);

        try {
            String responseStr = lazadaApiClient.executePost("/auth/token/create", params, null, null);
            Map<String, Object> responseMap = objectMapper.readValue(responseStr, new TypeReference<>() {});
            return responseMap;
        } catch (Exception e) {
            log.error("[LazadaOAuth] Failed to exchange token", e);
            throw new RuntimeException("Failed to exchange Lazada token", e);
        }
    }
}
