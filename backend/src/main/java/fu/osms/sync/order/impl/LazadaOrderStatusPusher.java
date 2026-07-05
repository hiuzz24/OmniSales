package fu.osms.sync.order.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.order.OrderStatusPushResult;
import fu.osms.sync.order.PlatformOrderStatusPusher;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LazadaOrderStatusPusher implements PlatformOrderStatusPusher {

    private final ChannelCredentialRepository credentialRepository;
    private final LazadaApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Value("${lazada.order.delivery-type:dropship}")
    private String deliveryType;

    @Value("${lazada.order.shipping-allocate-type:TFS}")
    private String shippingAllocateType;

    @Value("${lazada.order.cancel-reason-id:}")
    private String defaultCancelReasonId;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    public OrderStatusPushResult push(Order order, OrderStatus targetStatus, String cancelReason) {
        return switch (targetStatus) {
            case PROCESSING -> pack(order);
            case SHIPPED -> readyToShip(order);
            case CANCELLED -> cancel(order, cancelReason);
            default -> OrderStatusPushResult.skipped("Lazada does not support pushing " + targetStatus);
        };
    }

    private OrderStatusPushResult pack(Order order) {
        ChannelCredential credential = connectedCredential(order);
        Long tokenExpiresAt = tokenExpiresAt(credential);
        List<Map<String, Object>> items = fetchOrderItems(order, credential, tokenExpiresAt);
        List<String> orderItemIds = orderItemIds(items);
        if (orderItemIds.isEmpty()) {
            return OrderStatusPushResult.failed("No Lazada order item id found");
        }

        Map<String, String> params = new HashMap<>();
        params.put("delivery_type", deliveryType);
        params.put("shipping_allocate_type", shippingAllocateType);
        params.put("pack_order_list", toJson(List.of(Map.of(
                "order_id", order.getExternalOrderId(),
                "order_item_list", orderItemIds
        ))));

        Map<String, Object> body = executePost("/order/fulfill/pack", params, credential, tokenExpiresAt,
                "Lazada pack order API returned error");
        String packageId = extractPackageId(body);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orderItemIds", orderItemIds);
        if (packageId != null) {
            metadata.put("packageId", packageId);
        }
        mergePushMetadata(order, "lazada", "PROCESSING", metadata);
        return OrderStatusPushResult.success("Lazada order packed", metadata);
    }

    private OrderStatusPushResult readyToShip(Order order) {
        ChannelCredential credential = connectedCredential(order);
        Long tokenExpiresAt = tokenExpiresAt(credential);
        String packageId = existingPackageId(order);
        Map<String, Object> metadata = new HashMap<>();

        if (packageId == null || packageId.isBlank()) {
            OrderStatusPushResult packResult = pack(order);
            if (!packResult.isSuccess()) {
                return packResult;
            }
            metadata.putAll(packResult.getMetadata());
            Object packedPackageId = packResult.getMetadata().get("packageId");
            packageId = packedPackageId != null ? packedPackageId.toString() : null;
        }

        if (packageId == null || packageId.isBlank()) {
            return OrderStatusPushResult.failed("Missing Lazada package id for ready to ship");
        }

        Map<String, String> params = new HashMap<>();
        params.put("package_id", packageId);
        params.put("delivery_type", deliveryType);
        executePost("/order/package/rts", params, credential, tokenExpiresAt,
                "Lazada ready to ship API returned error");

        metadata.put("packageId", packageId);
        mergePushMetadata(order, "lazada", "SHIPPED", metadata);
        return OrderStatusPushResult.success("Lazada order marked ready to ship", metadata);
    }

    private OrderStatusPushResult cancel(Order order, String cancelReason) {
        ChannelCredential credential = connectedCredential(order);
        Long tokenExpiresAt = tokenExpiresAt(credential);
        List<Map<String, Object>> items = fetchOrderItems(order, credential, tokenExpiresAt);
        List<String> orderItemIds = orderItemIds(items);
        if (orderItemIds.isEmpty()) {
            return OrderStatusPushResult.failed("No Lazada order item id found");
        }

        String reasonId = resolveCancelReasonId(orderItemIds, cancelReason, credential, tokenExpiresAt);
        if (reasonId == null || reasonId.isBlank()) {
            return OrderStatusPushResult.failed("Missing Lazada cancel reason id");
        }

        Map<String, String> params = new HashMap<>();
        params.put("order_item_ids", toJson(orderItemIds));
        params.put("reason_id", reasonId);
        if (cancelReason != null && !cancelReason.isBlank()) {
            params.put("reason_detail", cancelReason);
        }
        executePost("/order/reverse/cancel", params, credential, tokenExpiresAt,
                "Lazada cancel order API returned error");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orderItemIds", orderItemIds);
        metadata.put("cancelReasonId", reasonId);
        mergePushMetadata(order, "lazada", "CANCELLED", metadata);
        return OrderStatusPushResult.success("Lazada order cancelled", metadata);
    }

    private List<Map<String, Object>> fetchOrderItems(Order order, ChannelCredential credential, Long tokenExpiresAt) {
        String response = lazadaApiClient.executeGet(
                "/order/items/get",
                Map.of("order_id", order.getExternalOrderId()),
                credential.getAccessToken(),
                tokenExpiresAt
        );
        Map<String, Object> body = WebhookPayloadUtils.parseObject(response, "Lazada order items response is invalid");
        assertLazadaSuccess(body, "Lazada order items API returned error");
        Object data = body.get("data");
        if (!(data instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(WebhookPayloadUtils::copyMap)
                .toList();
    }

    private String resolveCancelReasonId(List<String> orderItemIds, String cancelReason,
                                         ChannelCredential credential, Long tokenExpiresAt) {
        if (defaultCancelReasonId != null && !defaultCancelReasonId.isBlank()) {
            return defaultCancelReasonId;
        }

        Map<String, String> params = new HashMap<>();
        params.put("order_item_ids", toJson(orderItemIds));
        if (cancelReason != null && !cancelReason.isBlank()) {
            params.put("reason_detail", cancelReason);
        }
        Map<String, Object> body = executePost("/order/reverse/cancel/validate", params, credential, tokenExpiresAt,
                "Lazada cancel validate API returned error");
        return findReasonId(body);
    }

    private Map<String, Object> executePost(String apiPath, Map<String, String> params, ChannelCredential credential,
                                            Long tokenExpiresAt, String errorMessage) {
        String response = lazadaApiClient.executePost(apiPath, params, credential.getAccessToken(), tokenExpiresAt);
        Map<String, Object> body = WebhookPayloadUtils.parseObject(response, errorMessage);
        assertLazadaSuccess(body, errorMessage);
        return body;
    }

    private void assertLazadaSuccess(Map<String, Object> body, String errorMessage) {
        String code = WebhookPayloadUtils.text(body.get("code"));
        if (code != null && !"0".equals(code)) {
            String message = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(body, "message", "msg"));
            throw new IllegalStateException(errorMessage + (message != null ? ": " + message : ""));
        }
    }

    private List<String> orderItemIds(List<Map<String, Object>> items) {
        return items.stream()
                .map(item -> WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(item, "order_item_id", "trade_order_line_id")))
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String extractPackageId(Map<String, Object> body) {
        Map<String, Object> data = WebhookPayloadUtils.copyMap(body.get("data"));
        String direct = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(data, "package_id", "packageId"));
        if (direct != null && !direct.isBlank()) {
            return direct;
        }
        Object packages = data.get("packages");
        if (packages instanceof List<?> list && !list.isEmpty()) {
            Map<String, Object> first = WebhookPayloadUtils.copyMap(list.get(0));
            return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(first, "package_id", "packageId"));
        }
        return null;
    }

    private String findReasonId(Map<String, Object> body) {
        String direct = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(body, "reason_id", "reasonId"));
        if (direct != null && !direct.isBlank()) {
            return direct;
        }

        Object data = body.get("data");
        if (data instanceof Map<?, ?> map) {
            Map<String, Object> dataMap = WebhookPayloadUtils.copyMap(map);
            String fromData = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(dataMap, "reason_id", "reasonId", "id"));
            if (fromData != null && !fromData.isBlank()) {
                return fromData;
            }
            Object reasons = WebhookPayloadUtils.firstPresent(dataMap, "reasons", "reason_list", "reasonList");
            return firstReasonIdFromList(reasons);
        }
        return firstReasonIdFromList(data);
    }

    private String firstReasonIdFromList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(WebhookPayloadUtils::copyMap)
                .map(item -> WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(item, "reason_id", "reasonId", "id")))
                .filter(reasonId -> reasonId != null && !reasonId.isBlank())
                .findFirst()
                .orElse(null);
    }

    private ChannelCredential connectedCredential(Order order) {
        return credentialRepository.findByChannelIdAndConnectionState(order.getChannel().getId(), "CONNECTED")
                .filter(credential -> credential.getAccessToken() != null && !credential.getAccessToken().isBlank())
                .orElseThrow(() -> new IllegalStateException("Lazada channel credential not connected"));
    }

    private Long tokenExpiresAt(ChannelCredential credential) {
        return credential.getTokenExpiresAt() != null ? credential.getTokenExpiresAt().toEpochSecond() : null;
    }

    private String existingPackageId(Order order) {
        Map<String, Object> metadata = order.getPlatformMetadata();
        if (metadata == null) {
            return null;
        }
        Map<String, Object> lazada = WebhookPayloadUtils.copyMap(metadata.get("lazada"));
        Object packageId = lazada.get("packageId");
        return packageId != null ? packageId.toString() : null;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize Lazada order status payload", e);
        }
    }
}
