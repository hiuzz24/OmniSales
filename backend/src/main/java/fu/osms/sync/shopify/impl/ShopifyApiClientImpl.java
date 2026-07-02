package fu.osms.sync.shopify.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;
import fu.osms.sync.dto.shopify.response.ShopifyProductRootResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import fu.osms.sync.shopify.ShopifyApiClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyApiClientImpl implements ShopifyApiClient {

    private static final String API_VERSION = "2026-04";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ShopifyProductResponse createProduct(String shopDomain, String accessToken, ShopifyProductPayload payload) {
        String url = buildUrl(shopDomain, "/products.json");
        Map<String, Object> root = new HashMap<>();
        root.put("product", payload);
        return executeRequest(url, accessToken, HttpMethod.POST, root);
    }

    public ShopifyProductResponse updateProduct(String shopDomain, String accessToken, String externalProductId, ShopifyProductPayload payload) {
        String url = buildUrl(shopDomain, "/products/" + externalProductId + ".json");
        Map<String, Object> root = new HashMap<>();
        root.put("product", payload);
        return executeRequest(url, accessToken, HttpMethod.PUT, root);
    }

    private ShopifyProductResponse executeRequest(String url, String accessToken, HttpMethod method, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", accessToken);

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Shopify request payload", e);
        }

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(url, method, entity, String.class);
        } catch (org.springframework.web.client.RestClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("Shopify API Error: {} - {}", e.getStatusCode(), errorBody);
            throw new RuntimeException("Shopify API Error: " + errorBody, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute request to Shopify", e);
        }

        try {
            ShopifyProductRootResponse rootResponse = objectMapper.readValue(response.getBody(), ShopifyProductRootResponse.class);
            return rootResponse.getProduct();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Shopify response", e);
        }
    }

    private String buildUrl(String shopDomain, String path) {
        String domain = shopDomain.endsWith(".myshopify.com") ? shopDomain
                : shopDomain + ".myshopify.com";
        return "https://" + domain + "/admin/api/" + API_VERSION + path;
    }
}
