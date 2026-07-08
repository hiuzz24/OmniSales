package fu.osms.sync.order.impl;

import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.enums.ShopifyCancelReason;
import fu.osms.sync.order.OrderStatusPushContext;
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
    public OrderStatusPushResult push(Order order, OrderStatus targetStatus, OrderStatusPushContext context) {
        return switch (targetStatus) {
            case SHIPPED -> fulfill(order);
            case CANCELLED -> cancel(order, context);
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

        Map<String, Object> fulfillmentOrder = selectFulfillableOrder(fulfillmentOrders);
        if (fulfillmentOrder == null) {
            return OrderStatusPushResult.failed("No open Shopify fulfillment order found");
        }

        Object id = fulfillmentOrder.get("id");
        if (id == null || id.toString().isBlank()) {
            return OrderStatusPushResult.failed("Shopify fulfillment order id is missing");
        }

        String fulfillmentOrderId = String.valueOf(id);
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

    private Map<String, Object> selectFulfillableOrder(List<Map<String, Object>> fulfillmentOrders) {
        return fulfillmentOrders.stream()
                .filter(this::isOpenFulfillmentOrder)
                .filter(this::supportsCreateFulfillment)
                .findFirst()
                .orElseGet(() -> fulfillmentOrders.stream()
                        .filter(this::isOpenFulfillmentOrder)
                        .findFirst()
                        .orElse(null));
    }

    private boolean isOpenFulfillmentOrder(Map<String, Object> fulfillmentOrder) {
        Object status = fulfillmentOrder.get("status");
        return status != null && "open".equalsIgnoreCase(status.toString());
    }

    private boolean supportsCreateFulfillment(Map<String, Object> fulfillmentOrder) {
        Object actions = fulfillmentOrder.get("supported_actions");
        if (!(actions instanceof List<?> supportedActions)) {
            return false;
        }
        return supportedActions.stream()
                .filter(action -> action != null)
                .map(action -> action.toString().toLowerCase())
                .anyMatch(action -> action.contains("fulfill"));
    }

    private OrderStatusPushResult cancel(Order order, OrderStatusPushContext context) {
        ChannelCredential credential = connectedCredential(order);
        String shopDomain = shopDomain(order);
        ShopifyCancelReason reason = context.getShopifyReason() != null
                ? context.getShopifyReason()
                : ShopifyCancelReason.OTHER;
        boolean email = context.getEmail() == null || context.getEmail();
        boolean restock = context.getRestock() == null || context.getRestock();
        boolean refund = context.getRefund() == null || context.getRefund();

        Map<String, Object> cancelled =
                shopifyApiClient.cancelOrder(shopDomain, credential.getAccessToken(),
                        order.getExternalOrderId(), reason, email, restock, refund);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("shopifyReason", reason.name());
        metadata.put("reason", reason.getShopifyValue());
        metadata.put("email", email);
        metadata.put("restock", restock);
        metadata.put("refund", refund);
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
