package fu.osms.sync.webhook.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.webhook.PlatformWebhookHandler;
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
public class LazadaWebhookHandler implements PlatformWebhookHandler {

    private final ChannelRepository channelRepository;

    @Value("${lazada.app-key}")
    private String appKey;

    @Value("${lazada.app-secret}")
    private String appSecret;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    public boolean verify(Map<String, String> headers, String rawBody) {
        if (appSecret == null || appSecret.isBlank()) {
            return true;
        }
        String signature = headers.get("authorization");
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            String normalizedSignature = signature.trim().toLowerCase();
            String base = appKey + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculated = HexFormat.of().formatHex(mac.doFinal(base.getBytes(StandardCharsets.UTF_8))).toLowerCase();
            return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), normalizedSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String extractEventType(Map<String, String> headers, Map<String, Object> payload) {
        Object messageType = firstPresent(payload, "message_type");
        if (messageType == null) {
            return "UNKNOWN";
        }
        String type = messageType.toString();
        if ("0".equals(type)) {
            return "TRADE_ORDER";
        }
        if ("10".equals(type)) {
            return "REVERSE_ORDER";
        }
        return type;
    }

    @Override
    public String extractExternalEventId(Map<String, String> headers, Map<String, Object> payload, String rawBody) {
        return "lazada-" + sha256(rawBody);
    }

    @Override
    public Optional<Channel> resolveChannel(Map<String, String> headers, Map<String, Object> payload) {
        Object accountId = firstPresent(payload, "seller_id");
        if (accountId == null) {
            return Optional.empty();
        }
        return channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA).stream()
                .filter(channel -> channel.getMetadata() != null
                        && accountId.toString().equals(String.valueOf(channel.getMetadata().get("accountId"))))
                .findFirst();
    }

    private Object firstPresent(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) {
                return value;
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
}
