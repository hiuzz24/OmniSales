package fu.osms.sync.shopify.impl;

import fu.osms.sync.dto.shopify.WebhookRegistrationResult;
import fu.osms.sync.dto.shopify.response.ShopifyWebhookResponse;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyWebhookSubscriptionServiceImpl implements ShopifyWebhookSubscriptionService {

    private final ShopifyApiClient shopifyApiClient;
    private final ShopifyShopDomainNormalizer shopDomainNormalizer;

    @Value("${shopify.webhook-callback-url:}")
    private String callbackUrl;

    @Value("${shopify.webhook-topics:orders/create,orders/updated,orders/cancelled,products/update,inventory_levels/update}")
    private String configuredTopics;

    @Override
    public WebhookRegistrationResult registerWebhooks(String shopDomain, String accessToken) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return WebhookRegistrationResult.builder()
                    .status("SKIPPED")
                    .error("Missing shopify.webhook-callback-url")
                    .webhooks(Collections.emptyList())
                    .build();
        }

        String normalizedShop = shopDomainNormalizer.normalizeHandle(shopDomain);
        List<Map<String, Object>> registeredWebhooks = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        List<ShopifyWebhookResponse> existing;
        try {
            existing = shopifyApiClient.listWebhooks(normalizedShop, accessToken);
        } catch (Exception e) {
            log.error("[ShopifyWebhook] Failed to list webhooks for shop={}: {}", normalizedShop, e.getMessage());
            return WebhookRegistrationResult.builder()
                    .status("FAILED")
                    .error("Failed to list existing webhooks: " + e.getMessage())
                    .webhooks(Collections.emptyList())
                    .build();
        }

        List<String> webhookTopics = webhookTopics();
        Map<String, ShopifyWebhookResponse> canonicalByTopic = new LinkedHashMap<>();
        Set<Long> deletedDuplicateIds = new HashSet<>();
        for (ShopifyWebhookResponse webhook : existing) {
            if (isOsmsWebhook(webhook)) {
                if (!webhookTopics.contains(webhook.getTopic())) {
                    if (webhook.getId() != null) {
                        log.info("[ShopifyWebhook] Removing disabled topic={} id={} shop={}",
                                webhook.getTopic(), webhook.getId(), normalizedShop);
                        deleteWebhook(normalizedShop, accessToken, webhook.getId(), deletedDuplicateIds);
                    }
                    continue;
                }
                ShopifyWebhookResponse canonical = canonicalByTopic.putIfAbsent(webhook.getTopic(), webhook);
                if (canonical == null) {
                    registeredWebhooks.add(toMetadata(webhook));
                } else if (webhook.getId() != null) {
                    log.warn("[ShopifyWebhook] Removing duplicate topic={} id={} shop={}",
                            webhook.getTopic(), webhook.getId(), normalizedShop);
                    deleteWebhook(normalizedShop, accessToken, webhook.getId(), deletedDuplicateIds);
                }
            }
        }
        Set<String> registeredTopics = new HashSet<>(canonicalByTopic.keySet());

        for (String topic : webhookTopics) {
            if (registeredTopics.contains(topic)) {
                log.info("[ShopifyWebhook] Already registered topic={} for shop={}", topic, normalizedShop);
                continue;
            }
            try {
                ShopifyWebhookResponse created = shopifyApiClient.createWebhook(normalizedShop, accessToken, topic, callbackUrl);
                registeredWebhooks.add(toMetadata(created));
                registeredTopics.add(topic);
                log.info("[ShopifyWebhook] Registered topic={} for shop={}", topic, normalizedShop);
            } catch (Exception e) {
                errors.add(topic);
                log.error("[ShopifyWebhook] Failed topic={} shop={}: {}", topic, normalizedShop, e.getMessage());
            }
        }

        registerGraphQlReturnWebhooks(normalizedShop, accessToken, registeredWebhooks, errors);

        String status = errors.isEmpty()
                ? "SUCCESS"
                : errors.size() < webhookTopics.size() ? "PARTIAL" : "FAILED";
        String error = errors.isEmpty() ? null : "Failed to register: " + String.join(", ", errors);

        return WebhookRegistrationResult.builder()
                .status(status)
                .error(error)
                .webhooks(registeredWebhooks)
                .build();
    }

    @Override
    public void unregisterWebhooks(String shopDomain, String accessToken, List<Map<String, Object>> savedWebhooks) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            log.warn("[ShopifyWebhook] Missing callback URL, skip unregister for shop={}", shopDomain);
            return;
        }

        String normalizedShop = shopDomainNormalizer.normalizeHandle(shopDomain);
        Set<Long> deletedIds = new HashSet<>();

        if (savedWebhooks != null) {
            for (Map<String, Object> savedWebhook : savedWebhooks) {
                String address = String.valueOf(savedWebhook.get("address"));
                if (!callbackUrl.equals(address)) {
                    continue;
                }
                Object rawId = savedWebhook.get("id");
                if (rawId != null && rawId.toString().startsWith("gid://shopify/WebhookSubscription/")) {
                    deleteGraphQlWebhook(normalizedShop, accessToken, rawId.toString());
                    continue;
                }
                Long webhookId = toLong(rawId);
                if (webhookId == null) {
                    continue;
                }
                deleteWebhook(normalizedShop, accessToken, webhookId, deletedIds);
            }
        }

        try {
            List<ShopifyWebhookResponse> currentWebhooks = shopifyApiClient.listWebhooks(normalizedShop, accessToken);
            for (ShopifyWebhookResponse webhook : currentWebhooks) {
                if (webhook.getId() != null && isOsmsWebhook(webhook)) {
                    deleteWebhook(normalizedShop, accessToken, webhook.getId(), deletedIds);
                }
            }
        } catch (Exception e) {
            log.warn("[ShopifyWebhook] Fallback list/delete failed for shop={}: {}", normalizedShop, e.getMessage());
        }
    }

    private void deleteWebhook(String shopDomain, String accessToken, Long webhookId, Set<Long> deletedIds) {
        if (!deletedIds.add(webhookId)) {
            return;
        }
        try {
            shopifyApiClient.deleteWebhook(shopDomain, accessToken, webhookId);
            log.info("[ShopifyWebhook] Deleted id={} for shop={}", webhookId, shopDomain);
        } catch (Exception e) {
            log.error("[ShopifyWebhook] Failed delete id={} shop={}: {}", webhookId, shopDomain, e.getMessage());
        }
    }

    private boolean isOsmsWebhook(ShopifyWebhookResponse webhook) {
        return webhook != null
                && callbackUrl.equals(webhook.getAddress())
                && webhook.getTopic() != null;
    }

    private List<String> webhookTopics() {
        if (configuredTopics == null || configuredTopics.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(configuredTopics.split(","))
                .map(String::trim)
                .filter(topic -> !topic.isBlank())
                .distinct()
                .toList();
    }

    private Map<String, Object> toMetadata(ShopifyWebhookResponse webhook) {
        return Map.of(
                "id", webhook.getId() != null ? String.valueOf(webhook.getId()) : "",
                "topic", webhook.getTopic() != null ? webhook.getTopic() : "",
                "address", webhook.getAddress() != null ? webhook.getAddress() : ""
        );
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void registerGraphQlReturnWebhooks(String shopDomain,
                                               String accessToken,
                                               List<Map<String, Object>> registered,
                                               List<String> errors) {
        Map<String, Map<String, Object>> existing = graphQlWebhookSubscriptions(shopDomain, accessToken);
        for (String topic : graphQlReturnTopics()) {
            if (existing.containsKey(topic)) {
                registered.add(existing.get(topic));
                continue;
            }
            String mutation = """
                    mutation CreateReturnWebhook(
                      $topic: WebhookSubscriptionTopic!,
                      $subscription: WebhookSubscriptionInput!
                    ) {
                      webhookSubscriptionCreate(topic: $topic, webhookSubscription: $subscription) {
                        webhookSubscription { id topic uri }
                        userErrors { field message }
                      }
                    }
                    """;
            try {
                Map<String, Object> response = shopifyApiClient.executeGraphQl(
                        shopDomain,
                        accessToken,
                        mutation,
                        Map.of("topic", topic, "subscription", Map.of("uri", callbackUrl)));
                Map<String, Object> payload = object(object(response.get("data")).get("webhookSubscriptionCreate"));
                List<Map<String, Object>> userErrors = maps(payload.get("userErrors"));
                if (!userErrors.isEmpty()) {
                    throw new IllegalStateException(String.valueOf(userErrors));
                }
                Map<String, Object> subscription = object(payload.get("webhookSubscription"));
                registered.add(Map.of(
                        "id", String.valueOf(subscription.getOrDefault("id", "")),
                        "topic", String.valueOf(subscription.getOrDefault("topic", topic)),
                        "address", String.valueOf(subscription.getOrDefault("uri", callbackUrl))));
            } catch (Exception exception) {
                errors.add(topic);
                log.error("[ShopifyWebhook] Failed GraphQL return topic={} shop={}: {}",
                        topic, shopDomain, exception.getMessage());
            }
        }
    }

    private Map<String, Map<String, Object>> graphQlWebhookSubscriptions(String shopDomain, String accessToken) {
        String query = """
                query ReturnWebhookSubscriptions {
                  webhookSubscriptions(first: 250) {
                    nodes { id topic uri }
                  }
                }
                """;
        try {
            Map<String, Object> response = shopifyApiClient.executeGraphQl(shopDomain, accessToken, query, Map.of());
            Map<String, Object> connection = object(object(response.get("data")).get("webhookSubscriptions"));
            Map<String, Map<String, Object>> result = new LinkedHashMap<>();
            for (Map<String, Object> subscription : maps(connection.get("nodes"))) {
                if (!callbackUrl.equals(String.valueOf(subscription.get("uri")))) continue;
                String topic = String.valueOf(subscription.get("topic"));
                result.put(topic, Map.of(
                        "id", String.valueOf(subscription.getOrDefault("id", "")),
                        "topic", topic,
                        "address", callbackUrl));
            }
            return result;
        } catch (Exception exception) {
            log.warn("[ShopifyWebhook] Could not list GraphQL return webhooks shop={}: {}",
                    shopDomain, exception.getMessage());
            return Map.of();
        }
    }

    private void deleteGraphQlWebhook(String shopDomain, String accessToken, String id) {
        String mutation = """
                mutation DeleteReturnWebhook($id: ID!) {
                  webhookSubscriptionDelete(id: $id) {
                    deletedWebhookSubscriptionId
                    userErrors { field message }
                  }
                }
                """;
        try {
            shopifyApiClient.executeGraphQl(shopDomain, accessToken, mutation, Map.of("id", id));
        } catch (Exception exception) {
            log.warn("[ShopifyWebhook] Failed to delete GraphQL webhook id={}: {}", id, exception.getMessage());
        }
    }

    private List<String> graphQlReturnTopics() {
        return List.of(
                "RETURNS_REQUEST",
                "RETURNS_APPROVE",
                "RETURNS_DECLINE",
                "RETURNS_UPDATE",
                "RETURNS_PROCESS",
                "RETURNS_CANCEL",
                "REFUNDS_CREATE");
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(this::object).toList();
    }

}
