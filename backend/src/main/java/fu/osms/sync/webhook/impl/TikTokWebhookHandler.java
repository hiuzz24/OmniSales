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
        String authorization = headers.get("authorization");
        if (appKey == null || appKey.isBlank() || appSecret == null || appSecret.isBlank()
                || authorization == null || authorization.isBlank()) {
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
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String extractEventType(Map<String, String> headers, Map<String, Object> payload) {
        String type = WebhookPayloadUtils.text(payload.get("type"));
        if ("1".equals(type)) {
            return ORDER_STATUS_EVENT;
        }
        if ("2".equals(type)) {
            return REVERSE_STATUS_EVENT;
        }
        return "TIKTOK_TYPE_" + (type == null || type.isBlank() ? "UNKNOWN" : type);
    }

    @Override
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
        }
        return channelRepository.findActiveTikTokByShopId(shopId);
    }

    private String normalizeSignature(String authorization) {
        String candidate = authorization.trim().replaceFirst("(?i)^sha256=", "");
        String[] tokens = candidate.split("\\s+");
        candidate = tokens[tokens.length - 1];
        return candidate.replaceAll("[^0-9A-Fa-f]", "");
    }
}
