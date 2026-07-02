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
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyWebhookHandler implements PlatformWebhookHandler {

    private final ChannelRepository channelRepository;

    @Value("${shopify.api-secret:}")
    private String apiSecret;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.SHOPIFY;
    }

    @Override
    public boolean verify(Map<String, String> headers, String rawBody) {
        String hmac = header(headers, "x-shopify-hmac-sha256");
        if (apiSecret == null || apiSecret.isBlank() || hmac == null || hmac.isBlank()) {
            return false;
        }
        try {
            String normalizedHmac = hmac.trim().toLowerCase();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculated = Base64.getEncoder().encodeToString(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8))).trim().toLowerCase();
            return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), normalizedHmac.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String extractEventType(Map<String, String> headers, Map<String, Object> payload) {
        String topic = header(headers, "x-shopify-topic");
        if (topic != null && !topic.isBlank()) {
            return topic.toUpperCase().replace('/', '_');
        }
        return payload.get("id") != null ? "ORDERS_UNKNOWN" : "UNKNOWN";
    }

    @Override
    public String extractExternalEventId(Map<String, String> headers, Map<String, Object> payload, String rawBody) {
        String webhookId = header(headers, "x-shopify-webhook-id");
        if (webhookId != null && !webhookId.isBlank()) {
            return webhookId;
        }
        return "shopify-" + sha256(rawBody);
    }

    @Override
    public Optional<Channel> resolveChannel(Map<String, String> headers, Map<String, Object> payload) {
        String shopDomain = header(headers, "x-shopify-shop-domain");
        if (shopDomain == null || shopDomain.isBlank()) {
            return Optional.empty();
        }
        String normalized = shopDomain.endsWith(".myshopify.com")
                ? shopDomain.substring(0, shopDomain.length() - ".myshopify.com".length())
                : shopDomain;
        return channelRepository.findActiveShopifyByShopDomain(normalized);
    }

    private String header(Map<String, String> headers, String name) {
        return headers.get(name.toLowerCase());
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
