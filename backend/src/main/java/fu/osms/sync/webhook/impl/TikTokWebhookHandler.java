package fu.osms.sync.webhook.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.webhook.PlatformWebhookHandler;
import lombok.RequiredArgsConstructor;
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

@Component
@RequiredArgsConstructor
public class TikTokWebhookHandler implements PlatformWebhookHandler {

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
        if (!hasText(authorization)) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
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
        String eventType = text(payload, "event_type", "eventType", "type");
        if (hasText(eventType)) {
            return eventType.toUpperCase();
        }
        if (payload.containsKey("quantity_snapshot_after_change") || payload.containsKey("change_detail")) {
            return "INVENTORY_UPDATE";
        }
        return "UNKNOWN";
    }

    @Override
    public String extractExternalEventId(Map<String, String> headers,
                                         Map<String, Object> payload,
                                         String rawBody) {
        String eventId = text(payload, "event_id", "tts_notification_id", "notification_id");
        return hasText(eventId) ? eventId : "tiktok-" + sha256(rawBody);
    }

    @Override
    public Optional<Channel> resolveChannel(Map<String, String> headers, Map<String, Object> payload) {
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
