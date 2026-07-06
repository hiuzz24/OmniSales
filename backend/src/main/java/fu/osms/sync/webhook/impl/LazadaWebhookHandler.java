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
            String normalizedSignature = normalizeAuthorizationSignature(signature);
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
            Map<String, Object> data = dataPayload(payload);
            if (firstPresent(data, "sellableQuantity", "sellableStock", "availableStock", "quantity", "stock") != null) {
                return "INVENTORY_UPDATE";
            }
            if (firstPresent(data, "item_id", "itemId", "product_id", "productId", "sku_id", "seller_sku") != null) {
                return "PRODUCT_UPDATE";
            }
            return "UNKNOWN";
        }
        String type = messageType.toString();
        if ("0".equals(type)) {
            return "TRADE_ORDER";
        }
        if ("10".equals(type)) {
            return "REVERSE_ORDER";
        }
        if ("3".equals(type)) {
            return "PRODUCT_CREATE";
        }
        if ("4".equals(type)) {
            return "PRODUCT_UPDATE";
        }
        if ("5".equals(type)) {
            return "PRODUCT_DELETE";
        }
        if ("6".equals(type)) {
            return "INVENTORY_UPDATE";
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
            accountId = firstPresent(dataPayload(payload), "seller_id", "sellerId");
        }
        if (accountId == null) {
            return Optional.empty();
        }
        String resolvedAccountId = accountId.toString();
        return channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA).stream()
                .filter(channel -> channel.getMetadata() != null
                        && resolvedAccountId.equals(String.valueOf(channel.getMetadata().get("accountId"))))
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> dataPayload(Map<String, Object> payload) {
        Object data = payload == null ? null : payload.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return (Map<String, Object>) dataMap;
        }
        return Map.of();
    }

    private String normalizeAuthorizationSignature(String signature) {
        String normalized = signature.trim()
                .toLowerCase()
                .replace("sha256=", "");
        String[] tokens = normalized.split("\\s+");
        String candidate = tokens.length == 0 ? normalized : tokens[tokens.length - 1];
        String hexOnly = candidate.replaceAll("[^0-9a-f]", "");
        return hexOnly.length() > 64 ? hexOnly.substring(hexOnly.length() - 64) : hexOnly;
    }

    private Object firstPresent(Map<String, Object> payload, String... keys) {
        if (payload == null) {
            return null;
        }
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
