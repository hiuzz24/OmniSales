package fu.osms.sync.order.impl;

import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.order.OrderStatusPushResult;
import fu.osms.sync.order.PlatformOrderStatusPusher;
import fu.osms.sync.shopify.ShopifyApiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ShopifyOrderStatusPusher implements PlatformOrderStatusPusher {

    private final ChannelCredentialRepository credentialRepository;
    private final ShopifyApiClient shopifyApiClient;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.SHOPIFY;
    }

    @Override
    public OrderStatusPushResult push(Order order, OrderStatus targetStatus, String cancelReason) {
        return switch (targetStatus) {
            case SHIPPED -> fulfill(order);
            case CANCELLED -> cancel(order, cancelReason);
            default -> OrderStatusPushResult.skipped("Shopify does not support pushing " + targetStatus);
        };
    }

    private OrderStatusPushResult fulfill(Order order) {
        ChannelCredential credential = connectedCredential(order);
        String shopDomain = shopDomain(order);
        List<Map<String, Object>> fulfillmentOrders =
                shopifyApiClient.getFulfillmentOrders(shopDomain, credential.getAccessToken(), order.getExternalOrderId());
        if (fulfillmentOrders.isEmpty()) {
            return OrderStatusPushResult.failed("No Shopify fulfillment order found");
        }

        String fulfillmentOrderId = String.valueOf(fulfillmentOrders.get(0).get("id"));
        Map<String, Object> fulfillment =
                shopifyApiClient.createFulfillment(shopDomain, credential.getAccessToken(), fulfillmentOrderId, order.getTrackingNumber());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("fulfillmentOrderId", fulfillmentOrderId);
        if (fulfillment.get("id") != null) {
            metadata.put("fulfillmentId", String.valueOf(fulfillment.get("id")));
        }
        mergePushMetadata(order, "shopify", "SHIPPED", metadata);
        return OrderStatusPushResult.success("Shopify fulfillment created", metadata);
    }

    private OrderStatusPushResult cancel(Order order, String cancelReason) {
        ChannelCredential credential = connectedCredential(order);
        String shopDomain = shopDomain(order);
        Map<String, Object> cancelled =
                shopifyApiClient.cancelOrder(shopDomain, credential.getAccessToken(), order.getExternalOrderId(), cancelReason);

        Map<String, Object> metadata = new HashMap<>();
        if (cancelled.get("id") != null) {
            metadata.put("cancelledOrderId", String.valueOf(cancelled.get("id")));
        }
        mergePushMetadata(order, "shopify", "CANCELLED", metadata);
        return OrderStatusPushResult.success("Shopify order cancelled", metadata);
    }

    private ChannelCredential connectedCredential(Order order) {
        return credentialRepository.findByChannelIdAndConnectionState(order.getChannel().getId(), "CONNECTED")
                .filter(credential -> credential.getAccessToken() != null && !credential.getAccessToken().isBlank())
                .orElseThrow(() -> new IllegalStateException("Shopify channel credential not connected"));
    }

    private String shopDomain(Order order) {
        Map<String, Object> metadata = order.getChannel().getMetadata();
        Object value = metadata != null ? metadata.get("shopDomain") : null;
        if (value == null || value.toString().isBlank()) {
            throw new IllegalStateException("Shopify shopDomain is missing in channel metadata");
        }
        return value.toString();
    }

    private void mergePushMetadata(Order order, String platformKey, String targetStatus, Map<String, Object> pushMetadata) {
        Map<String, Object> metadata = order.getPlatformMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(order.getPlatformMetadata());
        Map<String, Object> platformMetadata = new HashMap<>(pushMetadata);
        platformMetadata.put("lastStatusPush", targetStatus);
        platformMetadata.put("lastStatusPushedAt", OffsetDateTime.now().toString());
        metadata.put(platformKey, platformMetadata);
        order.setPlatformMetadata(metadata);
    }
}
