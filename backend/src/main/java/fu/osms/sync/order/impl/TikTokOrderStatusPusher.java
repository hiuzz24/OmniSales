package fu.osms.sync.order.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.order.OrderStatusPushContext;
import fu.osms.sync.order.OrderStatusPushResult;
import fu.osms.sync.order.PlatformOrderStatusPusher;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TikTokOrderStatusPusher implements PlatformOrderStatusPusher {

    private static final Set<String> CANCELLABLE_STATUSES = Set.of("UNPAID", "ON_HOLD", "AWAITING_SHIPMENT");
    private static final Map<String, String> CANCEL_REASON_LABELS = Map.ofEntries(
            Map.entry("seller_cancel_unpaid_reason_out_of_stock", "Hết hàng"),
            Map.entry("seller_cancel_unpaid_reason_wrong_price", "Sai giá"),
            Map.entry("seller_cancel_unpaid_reason_buyer_hasnt_paid_within_time_allowed", "Khách hàng chưa thanh toán đúng hạn"),
            Map.entry("seller_cancel_unpaid_reason_buyer_requested_cancellation", "Khách hàng yêu cầu hủy"),
            Map.entry("seller_cancel_reason_out_of_stock", "Hết hàng"),
            Map.entry("seller_cancel_reason_wrong_price", "Sai giá"),
            Map.entry("seller_cancel_paid_reason_buyer_requested_cancellation", "Khách hàng yêu cầu hủy"),
            Map.entry("seller_cancel_paid_reason_address_not_deliver", "Không thể giao tới địa chỉ khách hàng"),
            Map.entry("seller_cancel_order_reason_potential_fraud", "Đơn hàng có nguy cơ gian lận cao")
    );

    private final TikTokOrderApiService tikTokOrderApiService;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public OrderStatusPushResult push(Order order, OrderStatus targetStatus, OrderStatusPushContext context) {
        return switch (targetStatus) {
            case SHIPPED -> ship(order);
            case CANCELLED -> cancel(order, context.getTikTokReason());
            default -> OrderStatusPushResult.skipped("TikTok does not support pushing " + targetStatus);
        };
    }

    @Override
    public List<CancelReasonResponse> getCancelReasons(Order order) {
        requireScope(order, "seller.return_refund.basic");
        Map<String, Object> detail = tikTokOrderApiService.getOrderDetail(order.getChannel(), order.getExternalOrderId());
        String rawStatus = rawStatus(detail);
        if (!CANCELLABLE_STATUSES.contains(rawStatus)) {
            throw new IllegalStateException("TikTok order cannot be cancelled in status " + rawStatus);
        }
        TikTokOrderApiService.Eligibility eligibility = tikTokOrderApiService
                .getSellerCancelEligibility(order.getChannel(), order.getExternalOrderId());
        if (!eligibility.eligible()) {
            throw new IllegalStateException(eligibility.warningMessage());
        }
        return eligibility.reasonNames().stream()
                .map(reason -> CancelReasonResponse.builder()
                        .id(reason)
                        .name(CANCEL_REASON_LABELS.getOrDefault(reason, reason))
                        .warningMessage(eligibility.warningMessage())
                        .build())
                .toList();
    }

    private OrderStatusPushResult ship(Order order) {
        requireScope(order, "seller.fulfillment.basic");
        Map<String, Object> detail = tikTokOrderApiService.getOrderDetail(order.getChannel(), order.getExternalOrderId());
        String rawStatus = rawStatus(detail);
        if (!"AWAITING_SHIPMENT".equals(rawStatus)) {
            return OrderStatusPushResult.failed("TikTok order must be AWAITING_SHIPMENT before shipping; current=" + rawStatus);
        }
        String shippingType = text(WebhookPayloadUtils.firstPresent(detail, "shipping_type", "delivery_type"));
        if (!"TIKTOK".equalsIgnoreCase(shippingType)) {
            return OrderStatusPushResult.failed("TikTok seller-shipping orders are not supported in this version");
        }

        Set<String> packageIds = packageIds(detail);
        if (packageIds.isEmpty()) {
            return OrderStatusPushResult.failed("TikTok order does not have a package to ship");
        }
        if (packageIds.size() != 1) {
            return OrderStatusPushResult.failed("TikTok orders with multiple packages are not supported in this version");
        }

        String packageId = packageIds.iterator().next();
        Map<String, Object> response = tikTokOrderApiService.shipPackage(order.getChannel(), packageId);
        Map<String, Object> pushMetadata = new LinkedHashMap<>();
        pushMetadata.put("packageId", packageId);
        putIfPresent(pushMetadata, "shipRequestId", text(response.get("request_id")));
        pushMetadata.put("lastStatusPush", OrderStatus.SHIPPED.name());
        pushMetadata.put("lastStatusPushedAt", OffsetDateTime.now().toString());
        mergeTikTokMetadata(order, pushMetadata);
        return OrderStatusPushResult.success("TikTok package marked ready for handover", pushMetadata);
    }

    private OrderStatusPushResult cancel(Order order, String reason) {
        requireScope(order, "seller.return_refund.basic");
        if (reason == null || reason.isBlank()) {
            return OrderStatusPushResult.failed("TikTok cancellation reason is required");
        }

        Map<String, Object> detail = tikTokOrderApiService.getOrderDetail(order.getChannel(), order.getExternalOrderId());
        String rawStatus = rawStatus(detail);
        if (!CANCELLABLE_STATUSES.contains(rawStatus)) {
            return OrderStatusPushResult.failed("TikTok order cannot be cancelled in status " + rawStatus);
        }

        TikTokOrderApiService.Eligibility eligibility = tikTokOrderApiService
                .getSellerCancelEligibility(order.getChannel(), order.getExternalOrderId());
        if (!eligibility.eligible() || !eligibility.reasonNames().contains(reason)) {
            return OrderStatusPushResult.failed("TikTok cancellation reason is no longer eligible for this order");
        }

        Map<String, Object> response = tikTokOrderApiService.cancelOrder(order.getChannel(), order.getExternalOrderId(), reason);
        Map<String, Object> data = WebhookPayloadUtils.copyMap(response.get("data"));
        String cancelStatus = text(data.get("cancel_status"));
        if (cancelStatus == null || cancelStatus.isBlank()) {
            return OrderStatusPushResult.failed("TikTok cancellation response is missing cancel status");
        }

        Map<String, Object> pushMetadata = new LinkedHashMap<>();
        putIfPresent(pushMetadata, "cancelId", text(data.get("cancel_id")));
        pushMetadata.put("cancelStatus", cancelStatus);
        pushMetadata.put("cancelReason", reason);
        pushMetadata.put("cancelRequestedAt", OffsetDateTime.now().toString());
        putIfPresent(pushMetadata, "cancelRequestId", text(response.get("request_id")));
        pushMetadata.put("pendingConfirmation", true);
        pushMetadata.put("lastStatusPush", OrderStatus.CANCELLED.name());
        pushMetadata.put("lastStatusPushedAt", OffsetDateTime.now().toString());
        mergeTikTokMetadata(order, pushMetadata);
        return OrderStatusPushResult.success("TikTok cancellation request accepted", pushMetadata);
    }

    private Set<String> packageIds(Map<String, Object> detail) {
        Set<String> result = new LinkedHashSet<>();
        maps(detail.get("packages")).forEach(item -> addIfPresent(result,
                text(WebhookPayloadUtils.firstPresent(item, "id", "package_id"))));
        maps(WebhookPayloadUtils.firstPresent(detail, "line_items", "order_line_items")).forEach(item -> addIfPresent(result,
                text(WebhookPayloadUtils.firstPresent(item, "package_id", "packageId"))));
        return result;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        list.stream().filter(Map.class::isInstance).map(WebhookPayloadUtils::copyMap).forEach(result::add);
        return result;
    }

    private void requireScope(Order order, String requiredScope) {
        Object configured = order.getChannel() != null && order.getChannel().getMetadata() != null
                ? order.getChannel().getMetadata().get("grantedScopes")
                : null;
        if (!(configured instanceof Collection<?> scopes) || scopes.isEmpty()) {
            return;
        }
        boolean granted = scopes.stream().map(String::valueOf).anyMatch(requiredScope::equals);
        if (!granted) {
            throw new IllegalStateException("TikTok authorization is missing " + requiredScope + ". Reconnect the channel.");
        }
    }

    private void mergeTikTokMetadata(Order order, Map<String, Object> changes) {
        Map<String, Object> metadata = order.getPlatformMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(order.getPlatformMetadata());
        Map<String, Object> tikTok = new HashMap<>(WebhookPayloadUtils.copyMap(metadata.get("tiktok")));
        tikTok.putAll(changes);
        metadata.put("tiktok", tikTok);
        order.setPlatformMetadata(metadata);
    }

    private String rawStatus(Map<String, Object> detail) {
        String value = text(WebhookPayloadUtils.firstPresent(detail, "status", "order_status"));
        return value == null ? "UNKNOWN" : value.toUpperCase();
    }

    private String text(Object value) {
        return WebhookPayloadUtils.text(value);
    }

    private void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value);
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }
}
