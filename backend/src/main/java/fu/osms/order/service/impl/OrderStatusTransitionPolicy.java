package fu.osms.order.service.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.service.OrderStockDeliveryReadinessService;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.order.OrderStatusPushResult;

import java.util.Map;
import java.util.UUID;

final class OrderStatusTransitionPolicy {

    private final OrderStockDeliveryReadinessService readinessService;

    OrderStatusTransitionPolicy(OrderStockDeliveryReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    void validate(Order order, OrderStatus oldStatus, OrderStatus targetStatus, UUID orderId) {
        if (targetStatus == OrderStatus.CANCELLED) {
            throw new AppException(ErrorCode.ORDER_CANCEL_ENDPOINT_REQUIRED);
        }
        validateTikTok(order, oldStatus, targetStatus);
        if (targetStatus == OrderStatus.SHIPPED) {
            if (oldStatus != OrderStatus.PROCESSING) {
                throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION);
            }
            readinessService.requireReadyForShipment(orderId);
        }
    }

    boolean shouldBlockLocalUpdate(Order order, OrderStatus status, OrderStatusPushResult pushResult) {
        if (!isPlatformOrder(order) || !requiresPlatformPush(order, status)) return false;
        if (isStrictPlatformOrder(order)) return !pushResult.isSuccess();
        return !pushResult.isSuccess() && !pushResult.isSkipped();
    }

    boolean shouldAutoMarkPaid(Order order, OrderStatus status) {
        return !isPlatformOrder(order) && status == OrderStatus.DELIVERED
                && "UNPAID".equals(order.getPaymentStatus());
    }

    boolean isPlatformOrder(Order order) {
        return order.getChannel() != null && order.getPlatform() != null
                && order.getPlatform() != PlatformType.MANUAL;
    }

    boolean requiresTextCancelReason(Order order) {
        return order.getPlatform() != PlatformType.LAZADA
                && order.getPlatform() != PlatformType.SHOPIFY
                && order.getPlatform() != PlatformType.TIKTOK;
    }

    boolean isTikTokCancellationPending(Order order) {
        if (order.getPlatformMetadata() == null) return false;
        Object rawTikTok = order.getPlatformMetadata().get("tiktok");
        if (!(rawTikTok instanceof Map<?, ?> tikTok)) return false;
        Object pending = tikTok.get("pendingConfirmation");
        return pending instanceof Boolean value ? value : Boolean.parseBoolean(String.valueOf(pending));
    }

    private boolean isStrictPlatformOrder(Order order) {
        return order.getPlatform() == PlatformType.LAZADA || order.getPlatform() == PlatformType.SHOPIFY
                || order.getPlatform() == PlatformType.TIKTOK;
    }

    private boolean requiresPlatformPush(Order order, OrderStatus status) {
        if (order.getPlatform() == PlatformType.LAZADA) {
            return status == OrderStatus.PROCESSING || status == OrderStatus.SHIPPED
                    || status == OrderStatus.CANCELLED;
        }
        if (order.getPlatform() == PlatformType.SHOPIFY || order.getPlatform() == PlatformType.TIKTOK) {
            return status == OrderStatus.SHIPPED || status == OrderStatus.CANCELLED;
        }
        return status == OrderStatus.CANCELLED;
    }

    private void validateTikTok(Order order, OrderStatus oldStatus, OrderStatus targetStatus) {
        if (order.getPlatform() != PlatformType.TIKTOK || oldStatus != OrderStatus.PENDING) return;
        if (targetStatus != OrderStatus.CONFIRMED && targetStatus != OrderStatus.PROCESSING
                && targetStatus != OrderStatus.SHIPPED) return;
        String rawStatus = tikTokRawOrderStatus(order);
        if (!"AWAITING_SHIPMENT".equalsIgnoreCase(rawStatus)) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION,
                    "TikTok chưa chuyển đơn sang AWAITING_SHIPMENT; trạng thái hiện tại="
                            + (rawStatus != null ? rawStatus : "UNKNOWN"));
        }
    }

    private String tikTokRawOrderStatus(Order order) {
        if (order.getPlatformMetadata() == null) return null;
        Object rawTikTok = order.getPlatformMetadata().get("tiktok");
        if (!(rawTikTok instanceof Map<?, ?> tikTok)) return null;
        Object rawStatus = tikTok.get("rawOrderStatus");
        return rawStatus == null ? null : String.valueOf(rawStatus);
    }
}
