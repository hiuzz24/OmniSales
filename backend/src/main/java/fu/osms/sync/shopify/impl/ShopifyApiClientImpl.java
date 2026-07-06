package fu.osms.sync.shopify.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;
import fu.osms.sync.dto.shopify.response.ShopifyProductRootResponse;
import fu.osms.sync.dto.shopify.response.ShopifyWebhookResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fu.osms.sync.shopify.ShopifyApiClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyApiClientImpl implements ShopifyApiClient {

    private static final String API_VERSION = "2026-07";
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

    @Override
    public ShopifyWebhookResponse createWebhook(String shopDomain, String accessToken, String topic, String callbackUrl) {
        log.info("4");
        String url = buildUrl(shopDomain, "/webhooks.json");
        log.info("[shopify url call to webhook]:{}",url);

        Map<String, Object> webhookBody = new HashMap<>();
        webhookBody.put("topic", topic);
        webhookBody.put("address", callbackUrl);
        webhookBody.put("format", "json");

        Map<String, Object> root = new HashMap<>();
        root.put("webhook", webhookBody);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", accessToken);

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize webhook request", e);
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            log.info("[shopify response]:{}",response);
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), new TypeReference<>() {
            });
            Object webhook = responseBody.get("webhook");
            return objectMapper.convertValue(webhook, ShopifyWebhookResponse.class);
        } catch (RestClientResponseException e) {
            log.error("Shopify createWebhook error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Shopify webhook creation failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Shopify webhook", e);
        }
    }

    @Override
    public List<ShopifyWebhookResponse> listWebhooks(String shopDomain, String accessToken) {
        String url = buildUrl(shopDomain, "/webhooks.json");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), new TypeReference<>() {
            });
            Object webhooks = responseBody.get("webhooks");
            if (webhooks == null) {
                return List.of();
            }
            return objectMapper.convertValue(webhooks, new TypeReference<List<ShopifyWebhookResponse>>() {
            });
        } catch (RestClientResponseException e) {
            log.error("Shopify listWebhooks error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Shopify list webhooks failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list Shopify webhooks", e);
        }
    }

    @Override
    public void deleteWebhook(String shopDomain, String accessToken, Long webhookId) {
        String url = buildUrl(shopDomain, "/webhooks/" + webhookId + ".json");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            restTemplate.exchange(url, HttpMethod.DELETE, entity, String.class);
        } catch (RestClientResponseException e) {
            log.error("Shopify deleteWebhook error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Shopify webhook deletion failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete Shopify webhook", e);
        }
    }

    @Override
    public List<String> listAccessScopes(String shopDomain, String accessToken) {
        String url = buildAdminUrl(shopDomain, "/oauth/access_scopes.json");

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            Map<String, Object> responseBody = objectMapper.readValue(response.getBody(), new TypeReference<>() {
            });
            Object accessScopes = responseBody.get("access_scopes");
            if (!(accessScopes instanceof List<?> scopes)) {
                return List.of();
            }
            return scopes.stream()
                    .filter(scope -> scope instanceof Map<?, ?>)
                    .map(scope -> ((Map<?, ?>) scope).get("handle"))
                    .filter(handle -> handle != null && !handle.toString().isBlank())
                    .map(Object::toString)
                    .toList();
        } catch (RestClientResponseException e) {
            log.error("Shopify listAccessScopes error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Shopify access scope check failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to list Shopify access scopes", e);
        }
    }

    @Override
    public Map<String, Object> executeGraphQl(String shopDomain,
                                              String accessToken,
                                              String query,
                                              Map<String, Object> variables) {
        String url = buildUrl(shopDomain, "/graphql.json");

        Map<String, Object> root = new HashMap<>();
        root.put("query", query);
        root.put("variables", variables == null ? Map.of() : variables);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", accessToken);

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(objectMapper.writeValueAsString(root), headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Shopify GraphQL request", e);
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            return objectMapper.readValue(response.getBody(), new TypeReference<>() {
            });
        } catch (RestClientResponseException e) {
            log.error("Shopify GraphQL error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Shopify GraphQL error: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute Shopify GraphQL request", e);
        }
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

    private String buildAdminUrl(String shopDomain, String path) {
        String domain = shopDomain.endsWith(".myshopify.com") ? shopDomain
                : shopDomain + ".myshopify.com";
        return "https://" + domain + "/admin" + path;
    }
}
