package fu.osms.sync.webhook.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.webhook.PlatformWebhookHandler;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class TikTokWebhookHandler implements PlatformWebhookHandler {

    private static final String ORDER_STATUS_EVENT = "TIKTOK_ORDER_STATUS_UPDATE";
    private static final String REVERSE_STATUS_EVENT = "TIKTOK_REVERSE_STATUS_UPDATE";
    private static final String INVENTORY_CHANGED_EVENT = "TIKTOK_INVENTORY_CHANGED";

    private final ChannelRepository channelRepository;

    @Value("${tiktok.app-key:}")
    private String appKey;

    @Value("${tiktok.app-secret:}")
    private String appSecret;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public boolean verify(Map<String, String> headers, String rawBody) {
        String authorization = headers.get("authorization");
        if (!hasText(appKey) || !hasText(appSecret) || !hasText(authorization) || rawBody == null) {
            return false;
        }

        try {
            String signature = normalizeSignature(authorization);
            if (signature.length() != 64) {
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((appKey + rawBody).getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(signature);
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    public String extractEventType(Map<String, String> headers, Map<String, Object> payload) {
        String type = WebhookPayloadUtils.text(payload.get("type"));
        if ("RETURN_STATUS_CHANGE".equalsIgnoreCase(type)
                || "RETURN_STATUS_CHANGED".equalsIgnoreCase(type)) {
            return type.toUpperCase();
        }
        if ("1".equals(type)) {
            return ORDER_STATUS_EVENT;
        }
        if ("2".equals(type)) {
            return REVERSE_STATUS_EVENT;
        }
        if ("68".equals(type)) {
            return INVENTORY_CHANGED_EVENT;
        }
        return "TIKTOK_TYPE_" + (hasText(type) ? type : "UNKNOWN");
    }

    @Override
    public String extractExternalEventId(Map<String, String> headers,
                                         Map<String, Object> payload,
                                         String rawBody) {
        String notificationId = "68".equals(WebhookPayloadUtils.text(payload.get("type")))
                ? WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "event_id"))
                : null;
        if (!hasText(notificationId)) {
            notificationId = WebhookPayloadUtils.text(payload.get("tts_notification_id"));
        }
        return hasText(notificationId) ? notificationId : "tiktok-" + sha256(rawBody);
    }

    @Override
    public boolean shouldIgnore(Map<String, Object> payload) {
        String type = WebhookPayloadUtils.text(payload.get("type"));
        return !"1".equals(type)
                && !"2".equals(type)
                && !"68".equals(type)
                && !"RETURN_STATUS_CHANGE".equalsIgnoreCase(type)
                && !"RETURN_STATUS_CHANGED".equalsIgnoreCase(type);
    }

    @Override
    public Optional<Channel> resolveChannel(Map<String, String> headers, Map<String, Object> payload) {
        if ("68".equals(WebhookPayloadUtils.text(payload.get("type")))) {
            String openId = WebhookPayloadUtils.text(payload.get("seller_open_id"));
            String sellerId = WebhookPayloadUtils.text(
                    WebhookPayloadUtils.firstPresentInData(payload, "seller_id"));
            List<Channel> candidates = channelRepository.findActiveTikTokByWebhookIdentity(
                    blankToEmpty(openId),
                    blankToEmpty(sellerId)
            );
            if (candidates.size() > 1) {
                log.error("[TikTokWebhook] Multiple active channels match inventory webhook openId={} sellerId={}",
                        openId, sellerId);
                return Optional.empty();
            }
            return candidates.stream().findFirst();
        }
        String shopId = WebhookPayloadUtils.text(payload.get("shop_id"));
        if (!hasText(shopId)) {
            return Optional.empty();
        }
        return channelRepository.findActiveTikTokByShopId(shopId);
    }

    private String normalizeSignature(String authorization) {
        String candidate = authorization.trim().replaceFirst("(?i)^sha256=", "");
        String[] tokens = candidate.split("\\s+");
        candidate = tokens[tokens.length - 1];
        return candidate.replaceAll("[^0-9A-Fa-f]", "");
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate TikTok webhook event ID", e);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }

    private String blankToEmpty(String value) {
        return hasText(value) ? value : "";
    }
}
