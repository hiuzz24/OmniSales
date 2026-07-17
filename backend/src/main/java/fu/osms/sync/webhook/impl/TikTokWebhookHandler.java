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
        if (!hasText(appKey) || !hasText(appSecret)) {
            return false;
        }
        String authorization = headers.get("authorization");
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()
                || authorization == null || authorization.isBlank()) {
        if (!hasText(authorization)) {
            return false;
        }
        try {
            String normalizedSignature = normalizeSignature(authorization);
            if (normalizedSignature.length() != 64) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((appKey + rawBody).getBytes(StandardCharsets.UTF_8));
            byte[] actual = HexFormat.of().parseHex(normalizedSignature);
            return MessageDigest.isEqual(expected, actual);
            String expected = HexFormat.of().formatHex(
                    mac.doFinal((appKey + rawBody).getBytes(StandardCharsets.UTF_8))
            );
            String actual = normalizeSignature(authorization);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    actual.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String extractEventType(Map<String, String> headers, Map<String, Object> payload) {
        String type = WebhookPayloadUtils.text(payload.get("type"));
        if ("1".equals(type)) {
            return ORDER_STATUS_EVENT;
        String eventType = text(payload, "event_type", "eventType", "type");
        if (hasText(eventType)) {
            return eventType.toUpperCase();
        }
        if ("2".equals(type)) {
            return REVERSE_STATUS_EVENT;
        if (payload.containsKey("quantity_snapshot_after_change") || payload.containsKey("change_detail")) {
            return "INVENTORY_UPDATE";
        }
        return "TIKTOK_TYPE_" + (type == null || type.isBlank() ? "UNKNOWN" : type);
        return "UNKNOWN";
    }

    @Override
    public String extractExternalEventId(Map<String, String> headers,
                                         Map<String, Object> payload,
                                         String rawBody) {
        String eventId = text(payload, "event_id", "tts_notification_id", "notification_id");
        return hasText(eventId) ? eventId : "tiktok-" + sha256(rawBody);
    public String extractExternalEventId(Map<String, String> headers, Map<String, Object> payload, String rawBody) {
        return WebhookPayloadUtils.text(payload.get("tts_notification_id"));
    }

    @Override
    public boolean shouldIgnore(Map<String, Object> payload) {
        String type = WebhookPayloadUtils.text(payload.get("type"));
        return !"1".equals(type) && !"2".equals(type);
    }

    @Override
    public Optional<Channel> resolveChannel(Map<String, String> headers, Map<String, Object> payload) {
        String shopId = WebhookPayloadUtils.text(payload.get("shop_id"));
        if (shopId == null || shopId.isBlank()) {
            return Optional.empty();
        String shopId = text(payload, "shop_id", "seller_id", "shopId", "sellerId");
        List<Channel> channels = channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.TIKTOK);
        if (hasText(shopId)) {
            Optional<Channel> matched = channels.stream()
                    .filter(channel -> matchesShop(channel, shopId))
                    .findFirst();
            if (matched.isPresent()) {
                return matched;
            }
        }
        return channelRepository.findActiveTikTokByShopId(shopId);
        return channels.size() == 1 ? Optional.of(channels.get(0)) : Optional.empty();
    }

    private boolean matchesShop(Channel channel, String shopId) {
        if (channel.getMetadata() == null) {
            return false;
        }
        for (String key : List.of("shopId", "shop_id", "accountId", "openId")) {
            Object value = channel.getMetadata().get(key);
            if (value != null && shopId.equals(value.toString())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSignature(String value) {
        String normalized = value.trim().toLowerCase().replace("sha256=", "");
        String[] tokens = normalized.split("\\s+");
        String candidate = tokens.length == 0 ? normalized : tokens[tokens.length - 1];
        String hex = candidate.replaceAll("[^0-9a-f]", "");
        return hex.length() > 64 ? hex.substring(hex.length() - 64) : hex;
    }

    private String normalizeSignature(String authorization) {
        String candidate = authorization.trim().replaceFirst("(?i)^sha256=", "");
        String[] tokens = candidate.split("\\s+");
        candidate = tokens[tokens.length - 1];
        return candidate.replaceAll("[^0-9A-Fa-f]", "");
    private String text(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null && hasText(value.toString())) {
                return value.toString();
            }
        }
        return null;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return String.valueOf(value.hashCode());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"null".equalsIgnoreCase(value);
    }
}
