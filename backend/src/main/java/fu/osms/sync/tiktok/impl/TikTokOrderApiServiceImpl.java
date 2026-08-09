package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;

@Service
@RequiredArgsConstructor
public class TikTokOrderApiServiceImpl implements TikTokOrderApiService {

    private final TikTokAuthorizedApiClient tikTokApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getOrderDetail(Channel channel, String orderId) {
        return getOrderDetails(channel, List.of(orderId)).get(0);
    }

    @Override
    public List<Map<String, Object>> getOrderDetails(Channel channel, List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty() || orderIds.size() > 50) {
            throw new IllegalArgumentException("TikTok order detail accepts between 1 and 50 order IDs");
        }
        String shopCipher = shopCipher(channel);
        String response = tikTokApiClient.executeGet(channel.getId(),
                "/order/202309/orders",
                Map.of("ids", String.join(",", orderIds), "shop_cipher", shopCipher)
        );
        Map<String, Object> root = parseSuccess(response, "TikTok order detail");
        Object rawOrders = WebhookPayloadUtils.copyMap(root.get("data")).get("orders");
        if (!(rawOrders instanceof List<?> orders) || orders.isEmpty()) {
            throw new IllegalStateException("TikTok order detail API returned no orders");
        }
        List<Map<String, Object>> mapped = orders.stream()
                .filter(Map.class::isInstance)
                .map(WebhookPayloadUtils::copyMap)
                .toList();
        Set<String> requested = new LinkedHashSet<>(orderIds);
        for (Map<String, Object> order : mapped) {
            String id = text(WebhookPayloadUtils.firstPresent(order, "id", "order_id"));
            if (!requested.contains(id)) throw new IllegalStateException("TikTok returned an unexpected order " + id);
        }
        Set<String> returned = new LinkedHashSet<>();
        mapped.forEach(order -> returned.add(text(WebhookPayloadUtils.firstPresent(order, "id", "order_id"))));
        requested.removeAll(returned);
        if (!requested.isEmpty()) throw new IllegalStateException("TikTok order detail is missing IDs: " + requested);
        Map<String, Map<String, Object>> byId = mapped.stream().collect(java.util.stream.Collectors.toMap(
                order -> text(WebhookPayloadUtils.firstPresent(order, "id", "order_id")), order -> order));
        return orderIds.stream().map(byId::get).toList();
    }

    @Override
    public OrderSearchPage searchOrders(Channel channel, OffsetDateTime from, OffsetDateTime to, String pageToken) {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("shop_cipher", shopCipher(channel));
        query.put("page_size", "100");
        if (pageToken != null && !pageToken.isBlank()) query.put("page_token", pageToken);
        OffsetDateTime exclusiveTo = exclusiveUpperBound(to);
        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                    "create_time_ge", from.toEpochSecond(),
                    "create_time_lt", exclusiveTo.toEpochSecond()));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize TikTok order search request", e);
        }
        String response = tikTokApiClient.executePost(channel.getId(), "/order/202309/orders/search", query, body);
        Map<String, Object> root = parseSuccess(response, "TikTok search orders");
        Map<String, Object> data = WebhookPayloadUtils.copyMap(root.get("data"));
        Object rawOrders = data.get("orders");
        List<String> ids = rawOrders instanceof List<?> orders ? orders.stream().filter(Map.class::isInstance)
                .map(WebhookPayloadUtils::copyMap)
                .map(order -> text(WebhookPayloadUtils.firstPresent(order, "id", "order_id")))
                .filter(value -> value != null && !value.isBlank()).toList() : List.of();
        return new OrderSearchPage(ids, text(data.get("next_page_token")));
    }

    private OffsetDateTime exclusiveUpperBound(OffsetDateTime to) {
        if (to.getSecond() == 0 && to.getNano() == 0) {
            return to.plusMinutes(1);
        }
        return to.plusSeconds(1).withNano(0);
    }

    @Override
    public Eligibility getSellerCancelEligibility(Channel channel, String orderId) {
        String shopCipher = shopCipher(channel);
        String response = tikTokApiClient.executeGet(channel.getId(),
                "/return_refund/202602/orders/" + orderId + "/aftersale_eligibility",
                Map.of(
                        "shop_cipher", shopCipher,
                        "initiate_aftersale_user", "SELLER",
                        "request_types", "CANCEL"
                )
        );
        Map<String, Object> root = parseSuccess(response, "TikTok aftersale eligibility");
        return parseCancelEligibility(WebhookPayloadUtils.copyMap(root.get("data")));
    }

    @Override
    public Map<String, Object> shipPackage(Channel channel, String packageId) {
        String response = tikTokApiClient.executePost(channel.getId(),
                "/fulfillment/202309/packages/" + packageId + "/ship",
                Map.of("shop_cipher", shopCipher(channel)),
                "{}"
        );
        return parseSuccess(response, "TikTok ship package");
    }

    @Override
    public ShippingDocumentResult getPackageShippingDocument(Channel channel, String packageId) {
        String response = tikTokApiClient.executeGet(channel.getId(),
                "/fulfillment/202309/packages/" + packageId + "/shipping_documents",
                Map.of(
                        "shop_cipher", shopCipher(channel),
                        "document_type", "SHIPPING_LABEL",
                        "document_size", "A6",
                        "document_format", "PDF"
                )
        );
        Map<String, Object> root = WebhookPayloadUtils.parseObject(
                response, "TikTok shipping document response is invalid");
        Map<String, Object> data = WebhookPayloadUtils.copyMap(root.get("data"));
        return new ShippingDocumentResult(
                text(root.get("code")),
                text(root.get("message")),
                text(data.get("doc_url"))
        );
    }

    @Override
    public Map<String, Object> cancelOrder(Channel channel, String orderId, String cancelReason) {
        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(Map.of(
                    "order_id", orderId,
                    "cancel_reason", cancelReason
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize TikTok cancellation request", e);
        }
        String response = tikTokApiClient.executePost(channel.getId(),
                "/return_refund/202602/cancellations",
                Map.of("shop_cipher", shopCipher(channel)),
                rawBody
        );
        return parseSuccess(response, "TikTok cancel order");
    }

    private String shopCipher(Channel channel) {
        if (channel == null) {
            throw new IllegalStateException("TikTok order is missing channel");
        }
        String shopCipher = text(channel.getMetadata() != null ? channel.getMetadata().get("shopCipher") : null);
        if (shopCipher == null || shopCipher.isBlank()) {
            throw new IllegalStateException("TikTok shop cipher is missing. Reconnect the channel.");
        }
        return shopCipher;
    }

    private Map<String, Object> parseSuccess(String response, String operation) {
        Map<String, Object> root = WebhookPayloadUtils.parseObject(response, operation + " response is invalid");
        String code = text(root.get("code"));
        if (code != null && !"0".equals(code)) {
            throw new IllegalStateException(operation + " API returned error: " + text(root.get("message")));
        }
        return root;
    }

    private Eligibility parseCancelEligibility(Map<String, Object> data) {
        Object rawSkus = data.get("sku_eligibility");
        if (!(rawSkus instanceof List<?> skus) || skus.isEmpty()) {
            return new Eligibility(false, Set.of(), "TikTok did not return cancellation eligibility for this order");
        }

        List<Set<String>> reasonSets = new ArrayList<>();
        List<String> ineligibleReasons = new ArrayList<>();
        boolean foundCancelEntry = false;
        for (Object rawSku : skus) {
            Map<String, Object> sku = WebhookPayloadUtils.copyMap(rawSku);
            Object rawEntries = WebhookPayloadUtils.firstPresent(sku, "line_item_eligibility", "line_item_eligibilities");
            if (!(rawEntries instanceof List<?> entries)) {
                continue;
            }
            for (Object rawEntry : entries) {
                Map<String, Object> entry = WebhookPayloadUtils.copyMap(rawEntry);
                if (!"CANCEL".equalsIgnoreCase(text(entry.get("request_type")))) {
                    continue;
                }
                foundCancelEntry = true;
                if (!booleanValue(entry.get("eligible"))) {
                    String reason = text(entry.get("ineligible_reason"));
                    if (reason != null && !reason.isBlank()) {
                        ineligibleReasons.add(reason);
                    }
                    continue;
                }
                reasonSets.add(reasonNames(entry.get("available_reason_names")));
            }
        }

        if (!foundCancelEntry || reasonSets.isEmpty() || !ineligibleReasons.isEmpty()) {
            String warning = ineligibleReasons.isEmpty()
                    ? "TikTok does not allow this order to be cancelled"
                    : String.join("; ", new LinkedHashSet<>(ineligibleReasons));
            return new Eligibility(false, Set.of(), warning);
        }

        Set<String> common = new LinkedHashSet<>(reasonSets.get(0));
        reasonSets.stream().skip(1).forEach(common::retainAll);
        if (common.isEmpty()) {
            return new Eligibility(false, Set.of(), "No cancellation reason is valid for every item in this order");
        }
        return new Eligibility(true, common, null);
    }

    private Set<String> reasonNames(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof Collection<?> values) {
            values.stream().map(this::text).filter(item -> item != null && !item.isBlank()).forEach(result::add);
        } else {
            String reason = text(value);
            if (reason != null && !reason.isBlank()) {
                result.add(reason);
            }
        }
        return result;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(text(value));
    }

    private String text(Object value) {
        return WebhookPayloadUtils.text(value);
    }

}
