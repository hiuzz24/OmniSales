package fu.osms.sync.shopify.impl;

import fu.osms.sync.dto.shopify.WebhookRegistrationResult;
import fu.osms.sync.dto.shopify.response.ShopifyWebhookResponse;
import fu.osms.sync.shopify.ShopifyApiClient;
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

        String normalizedShop = normalizeShop(shopDomain);
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

        String normalizedShop = normalizeShop(shopDomain);
        Set<Long> deletedIds = new HashSet<>();

        if (savedWebhooks != null) {
            for (Map<String, Object> savedWebhook : savedWebhooks) {
                String address = String.valueOf(savedWebhook.get("address"));
                if (!callbackUrl.equals(address)) {
                    continue;
                }
                Long webhookId = toLong(savedWebhook.get("id"));
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

    private String normalizeShop(String shop) {
        if (shop == null || shop.isBlank()) {
            return shop;
        }
        return shop.endsWith(".myshopify.com")
                ? shop.substring(0, shop.length() - ".myshopify.com".length())
                : shop;
    }
}
