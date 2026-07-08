package fu.osms.sync.shopify.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.order.enums.ShopifyCancelReason;
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
    public List<Map<String, Object>> getFulfillmentOrders(String shopDomain, String accessToken, String orderId) {
        String url = buildUrl(shopDomain, "/orders/" + orderId + "/fulfillment_orders.json");
        Map<String, Object> response = executeRaw(url, accessToken, HttpMethod.GET, null, "get Shopify fulfillment orders");
        Object fulfillmentOrders = response.get("fulfillment_orders");
        if (fulfillmentOrders == null) {
            return List.of();
        }
        return objectMapper.convertValue(fulfillmentOrders, new TypeReference<List<Map<String, Object>>>() {
        });
    }

    @Override
    public Map<String, Object> createFulfillment(String shopDomain, String accessToken, String fulfillmentOrderId, String trackingNumber) {
        String url = buildUrl(shopDomain, "/fulfillments.json");

        Map<String, Object> fulfillmentOrder = new HashMap<>();
        fulfillmentOrder.put("fulfillment_order_id", numericIfPossible(fulfillmentOrderId));

        Map<String, Object> fulfillment = new HashMap<>();
        fulfillment.put("notify_customer", true);
        fulfillment.put("line_items_by_fulfillment_order", List.of(fulfillmentOrder));
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            fulfillment.put("tracking_info", Map.of("number", trackingNumber));
        }

        Map<String, Object> root = Map.of("fulfillment", fulfillment);
        Map<String, Object> response = executeRaw(url, accessToken, HttpMethod.POST, root, "create Shopify fulfillment");
        Object created = response.get("fulfillment");
        return created != null ? objectMapper.convertValue(created, new TypeReference<Map<String, Object>>() {
        }) : response;
    }

    @Override
    public Map<String, Object> cancelOrder(String shopDomain, String accessToken, String orderId,
                                           ShopifyCancelReason reason, boolean email, boolean restock, boolean refund) {
        String url = buildUrl(shopDomain, "/orders/" + orderId + "/cancel.json");
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("restock", restock);
        body.put("refund", refund);
        body.put("reason", reason != null ? reason.getShopifyValue() : ShopifyCancelReason.OTHER.getShopifyValue());
        Map<String, Object> response = executeRaw(url, accessToken, HttpMethod.POST, body, "cancel Shopify order");
        Object order = response.get("order");
        return order != null ? objectMapper.convertValue(order, new TypeReference<Map<String, Object>>() {
        }) : response;
    }

    private Map<String, Object> executeRaw(String url, String accessToken, HttpMethod method, Object body, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Shopify-Access-Token", accessToken);
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }

        HttpEntity<String> entity;
        try {
            entity = new HttpEntity<>(body != null ? objectMapper.writeValueAsString(body) : null, headers);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize Shopify request for " + action, e);
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
            return objectMapper.readValue(response.getBody(), new TypeReference<>() {
            });
        } catch (RestClientResponseException e) {
            log.error("Shopify {} error: {} - {}", action, e.getStatusCode(), e.getResponseBodyAsString());
            throw new RuntimeException("Shopify " + action + " failed: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to " + action, e);
        }
    }

    private Object numericIfPossible(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return value;
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
}
