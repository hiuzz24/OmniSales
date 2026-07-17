package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.sync.tiktok.TikTokApiClient;
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

@Service
@RequiredArgsConstructor
public class TikTokOrderApiServiceImpl implements TikTokOrderApiService {

    private final ChannelCredentialRepository credentialRepository;
    private final TikTokApiClient tikTokApiClient;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> getOrderDetail(Channel channel, String orderId) {
        Access access = access(channel);
        String response = tikTokApiClient.executeGet(
                "/order/202309/orders",
                Map.of("ids", orderId, "shop_cipher", access.shopCipher()),
                access.accessToken()
        );
        Map<String, Object> root = parseSuccess(response, "TikTok order detail");
        Object rawOrders = WebhookPayloadUtils.copyMap(root.get("data")).get("orders");
        if (!(rawOrders instanceof List<?> orders) || orders.isEmpty()) {
            throw new IllegalStateException("TikTok order detail API returned no order for " + orderId);
        }
        return orders.stream()
                .filter(Map.class::isInstance)
                .map(WebhookPayloadUtils::copyMap)
                .filter(order -> orderId.equals(text(WebhookPayloadUtils.firstPresent(order, "id", "order_id"))))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("TikTok order detail response does not contain order " + orderId));
    }

    @Override
    public Eligibility getSellerCancelEligibility(Channel channel, String orderId) {
        Access access = access(channel);
        String response = tikTokApiClient.executeGet(
                "/return_refund/202602/orders/" + orderId + "/aftersale_eligibility",
                Map.of(
                        "shop_cipher", access.shopCipher(),
                        "initiate_aftersale_user", "SELLER",
                        "request_types", "CANCEL"
                ),
                access.accessToken()
        );
        Map<String, Object> root = parseSuccess(response, "TikTok aftersale eligibility");
        return parseCancelEligibility(WebhookPayloadUtils.copyMap(root.get("data")));
    }

    @Override
    public Map<String, Object> shipPackage(Channel channel, String packageId) {
        Access access = access(channel);
        String response = tikTokApiClient.executePost(
                "/fulfillment/202309/packages/" + packageId + "/ship",
                Map.of("shop_cipher", access.shopCipher()),
                "{}",
                access.accessToken()
        );
        return parseSuccess(response, "TikTok ship package");
    }

    @Override
    public Map<String, Object> cancelOrder(Channel channel, String orderId, String cancelReason) {
        Access access = access(channel);
        String rawBody;
        try {
            rawBody = objectMapper.writeValueAsString(Map.of(
                    "order_id", orderId,
                    "cancel_reason", cancelReason
            ));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot serialize TikTok cancellation request", e);
        }
        String response = tikTokApiClient.executePost(
                "/return_refund/202602/cancellations",
                Map.of("shop_cipher", access.shopCipher()),
                rawBody,
                access.accessToken()
        );
        return parseSuccess(response, "TikTok cancel order");
    }

    private Access access(Channel channel) {
        if (channel == null) {
            throw new IllegalStateException("TikTok order is missing channel");
        }
        ChannelCredential credential = credentialRepository
                .findByChannelIdAndConnectionState(channel.getId(), "CONNECTED")
                .filter(value -> value.getAccessToken() != null && !value.getAccessToken().isBlank())
                .orElseThrow(() -> new IllegalStateException("TikTok channel credential is not connected"));
        String shopCipher = text(channel.getMetadata() != null ? channel.getMetadata().get("shopCipher") : null);
        if (shopCipher == null || shopCipher.isBlank()) {
            throw new IllegalStateException("TikTok shop cipher is missing. Reconnect the channel.");
        }
        return new Access(credential.getAccessToken(), shopCipher);
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

    private record Access(String accessToken, String shopCipher) {
    }
}
