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
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

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
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String calculated = Base64.getEncoder().encodeToString(mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(calculated.getBytes(StandardCharsets.UTF_8), hmac.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String extractEventType(Map<String, String> headers, Map<String, Object> payload) {
        String topic = header(headers, "x-shopify-topic");
        return topic != null ? topic.toUpperCase().replace('/', '_') : "UNKNOWN";
    }

    @Override
    public String extractExternalEventId(Map<String, String> headers, Map<String, Object> payload, String rawBody) {
        String webhookId = header(headers, "x-shopify-webhook-id");
        if (webhookId != null && !webhookId.isBlank()) {
            return webhookId;
        }
        Object id = payload.get("id");
        return id != null ? "shopify-" + id : null;
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
}
