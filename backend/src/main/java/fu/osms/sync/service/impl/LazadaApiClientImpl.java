package fu.osms.sync.service.impl;

import fu.osms.common.exception.TokenExpiredException;
import fu.osms.sync.service.LazadaApiClient;
import fu.osms.sync.util.LazadaSignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LazadaApiClientImpl implements LazadaApiClient {

    @Value("${lazada.app-key}")
    private String appKey;

    @Value("${lazada.app-secret}")
    private String appSecret;

    @Value("${lazada.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate;

    @Override
    public String executePost(String apiPath, Map<String, String> businessParams, String accessToken, Long tokenExpiresAt) {
        if (tokenExpiresAt != null && Instant.now().getEpochSecond() > tokenExpiresAt) {
            throw new TokenExpiredException("Lazada access token has expired. Please reconnect the channel.");
        }

        Map<String, String> allParams = new HashMap<>();
        if (businessParams != null) {
            allParams.putAll(businessParams);
        }

        allParams.put("app_key", appKey);
        allParams.put("timestamp", String.valueOf(Instant.now().toEpochMilli()));
        allParams.put("sign_method", "sha256");
        if (accessToken != null && !accessToken.isBlank()) {
            allParams.put("access_token", accessToken);
        }

        String signature = LazadaSignatureUtil.generateSignature(apiPath, allParams, appSecret);
        allParams.put("sign", signature);

        String fullUrl = apiUrl + apiPath;

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            body.add(entry.getKey(), entry.getValue());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            log.info("[LazadaApiClient] Calling {}, params: {}", fullUrl, allParams.keySet());
            ResponseEntity<String> response = restTemplate.postForEntity(fullUrl, request, String.class);
            return response.getBody();
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("[LazadaApiClient] API Error: {} - {}", e.getStatusCode(), errorBody);
            throw new RuntimeException("Lazada API Error: " + errorBody, e);
        } catch (Exception e) {
            log.error("[LazadaApiClient] Failed to execute request", e);
            throw new RuntimeException("Failed to execute request to Lazada: " + e.getMessage(), e);
        }
    }
}
