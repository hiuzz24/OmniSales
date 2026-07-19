package fu.osms.sync.order.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.order.OrderStatusPushContext;
import fu.osms.sync.order.OrderStatusPushResult;
import fu.osms.sync.order.PlatformOrderStatusPusher;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LazadaOrderStatusPusher implements PlatformOrderStatusPusher {

    private final LazadaAuthorizedApiClient lazadaApiClient;
    private final ObjectMapper objectMapper;

    @Value("${lazada.order.delivery-type:dropship}")
    private String deliveryType;

    @Value("${lazada.order.shipping-allocate-type:TFS}")
    private String shippingAllocateType;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    public OrderStatusPushResult push(Order order, OrderStatus targetStatus, OrderStatusPushContext context) {
        return switch (targetStatus) {
            case PROCESSING -> pack(order);
            case SHIPPED -> readyToShip(order);
            case CANCELLED -> cancel(order, context.getCancelReasonId());
            default -> OrderStatusPushResult.skipped("Lazada does not support pushing " + targetStatus);
        };
    }

    @Override
    public List<CancelReasonResponse> getCancelReasons(Order order) {
        List<String> orderItemIds = orderItemIds(fetchOrderItems(order));
        if (orderItemIds.isEmpty()) {
            return List.of();
        }

        Map<String, Object> body = validateCancel(order, orderItemIds);
        Map<String, Object> data = WebhookPayloadUtils.copyMap(body.get("data"));
        String warningMessage = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(data, "tip_content", "tipContent"));
        Object reasonOptions = WebhookPayloadUtils.firstPresent(data, "reason_options", "reasonOptions");
        if (!(reasonOptions instanceof List<?> list)) {
            return List.of();
        }

        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(WebhookPayloadUtils::copyMap)
                .map(item -> CancelReasonResponse.builder()
                        .id(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(item, "reason_id", "reasonId", "id")))
                        .name(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(item, "reason_name", "reasonName", "name")))
                        .warningMessage(warningMessage)
                        .build())
                .filter(reason -> reason.getId() != null && !reason.getId().isBlank())
                .toList();
    }

    private OrderStatusPushResult pack(Order order) {
        List<Map<String, Object>> items = fetchOrderItems(order);
        List<String> orderItemIds = orderItemIds(items);
        if (orderItemIds.isEmpty()) {
            return OrderStatusPushResult.failed("No Lazada order item id found");
        }

        Map<String, String> params = new HashMap<>();
        params.put("packReq", toJson(Map.of(
                "delivery_type", deliveryType,
                "shipping_allocate_type", shippingAllocateType,
                "pack_order_list", List.of(Map.of(
                        "order_id", order.getExternalOrderId(),
                        "order_item_list", orderItemIds
                ))
        )));

        Map<String, Object> body = executePost(order, "/order/fulfill/pack", params,
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
        params.put("readyToShipReq", toJson(Map.of(
                "packages", List.of(Map.of("package_id", packageId))
        )));
        executePost(order, "/order/package/rts", params,
                "Lazada ready to ship API returned error");

        metadata.put("packageId", packageId);
        mergePushMetadata(order, "lazada", "SHIPPED", metadata);
        return OrderStatusPushResult.success("Lazada order marked ready to ship", metadata);
    }

    private OrderStatusPushResult cancel(Order order, String reasonId) {
        List<Map<String, Object>> items = fetchOrderItems(order);
        List<String> orderItemIds = orderItemIds(items);
        if (orderItemIds.isEmpty()) {
            return OrderStatusPushResult.failed("No Lazada order item id found");
        }

        if (reasonId == null || reasonId.isBlank()) {
            return OrderStatusPushResult.failed("Missing Lazada cancel reason id");
        }

        Map<String, String> params = new HashMap<>();
        params.put("order_id", order.getExternalOrderId());
        params.put("order_item_id_list", toJson(orderItemIds));
        params.put("reason_id", reasonId);
        executeGet(order, "/order/reverse/cancel/create", params,
                "Lazada cancel order API returned error");

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("orderItemIds", orderItemIds);
        metadata.put("cancelReasonId", reasonId);
        mergePushMetadata(order, "lazada", "CANCELLED", metadata);
        return OrderStatusPushResult.success("Lazada order cancelled", metadata);
    }

    private List<Map<String, Object>> fetchOrderItems(Order order) {
        String response = lazadaApiClient.executeGet(order.getChannel().getId(),
                "/order/items/get",
                Map.of("order_id", order.getExternalOrderId())
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

    private Map<String, Object> validateCancel(Order order, List<String> orderItemIds) {
        Map<String, String> params = new HashMap<>();
        params.put("order_id", order.getExternalOrderId());
        params.put("order_item_id_list", toJson(orderItemIds));
        return executeGet(order, "/order/reverse/cancel/validate", params,
                "Lazada cancel validate API returned error");
    }

    private Map<String, Object> executeGet(Order order, String apiPath, Map<String, String> params,
                                           String errorMessage) {
        String response = lazadaApiClient.executeGet(order.getChannel().getId(), apiPath, params);
        Map<String, Object> body = WebhookPayloadUtils.parseObject(response, errorMessage);
        assertLazadaSuccess(body, errorMessage);
        return body;
    }

    private Map<String, Object> executePost(Order order, String apiPath, Map<String, String> params,
                                            String errorMessage) {
        String response = lazadaApiClient.executePost(order.getChannel().getId(), apiPath, params);
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
        Map<String, Object> result = WebhookPayloadUtils.copyMap(body.get("result"));
        Map<String, Object> data = WebhookPayloadUtils.copyMap(result.get("data"));
        String packageId = firstPackageIdFromPackOrderList(data.get("pack_order_list"));
        if (packageId != null && !packageId.isBlank()) {
            return packageId;
        }

        Object packages = data.get("packages");
        if (!(packages instanceof List<?> list) || list.isEmpty()) {
            return null;
        }

        Map<String, Object> firstPackage = WebhookPayloadUtils.copyMap(list.get(0));
        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(firstPackage, "package_id"));
    }

    private String firstPackageIdFromPackOrderList(Object value) {
        if (!(value instanceof List<?> packOrderList)) {
            return null;
        }

        for (Object packOrder : packOrderList) {
            Map<String, Object> packOrderMap = WebhookPayloadUtils.copyMap(packOrder);
            Object orderItems = packOrderMap.get("order_item_list");
            if (!(orderItems instanceof List<?> orderItemList)) {
                continue;
            }

            for (Object orderItem : orderItemList) {
                Map<String, Object> orderItemMap = WebhookPayloadUtils.copyMap(orderItem);
                String packageId = WebhookPayloadUtils.text(
                        WebhookPayloadUtils.firstPresent(orderItemMap, "package_id", "packageId"));
                if (packageId != null && !packageId.isBlank()) {
                    return packageId;
                }
            }
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
            Object reasons = WebhookPayloadUtils.firstPresent(dataMap, "reason_options", "reasons", "reason_list", "reasonList");
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
