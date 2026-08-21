package fu.osms.messaging.handler;

import fu.osms.common.enums.PlatformType;
import fu.osms.messaging.dto.OrderStockWaitingMessage;
import fu.osms.order.entity.Order;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderStockAllocationService;
import fu.osms.order.support.TikTokBuyerCancellationMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStockWaitingHandler {
    private static final int BATCH_SIZE = 50;
    private static final int PENDING_BATCH_SIZE = 500;

    private final OrderRepository orderRepository;
    private final OrderStockAllocationService allocationService;

    /** Duyệt FIFO, nhưng bỏ qua order cũ không thể đáp ứng đủ toàn bộ SKU. */
    public void handle(OrderStockWaitingMessage message) {
        if (message == null || message.variantIds() == null || message.variantIds().isEmpty()) return;
        for (UUID orderId : orderRepository.findPendingStockCandidateIds(
                message.variantIds(), PENDING_BATCH_SIZE)) {
            try {
                allocationService.movePendingOrderToWaitingIfUnavailable(orderId);
            } catch (Exception exception) {
                log.warn("[OrderStockWaiting] Pending reclassification failed messageId={} orderId={}",
                        message.messageId(), orderId, exception);
                throw exception;
            }
        }
        for (UUID orderId : orderRepository.findWaitingStockCandidateIds(message.variantIds(), BATCH_SIZE)) {
            if (!tiktokStillAwaitingShipment(orderId)) continue;
            try {
                allocationService.reconcileWaitingOrder(orderId);
            } catch (Exception exception) {
                log.warn("[OrderStockWaiting] Reconcile failed messageId={} orderId={}",
                        message.messageId(), orderId, exception);
                throw exception;
            }
        }
    }

    @Transactional(readOnly = true)
    protected boolean tiktokStillAwaitingShipment(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getPlatform() != PlatformType.TIKTOK) return true;
        if (TikTokBuyerCancellationMetadata.isActive(order)) return false;
        if (order.getPlatformMetadata() == null) return false;
        Object rawTikTok = order.getPlatformMetadata().get("tiktok");
        if (!(rawTikTok instanceof Map<?, ?> tikTok)) return false;
        Object status = tikTok.get("rawOrderStatus");
        return status != null && "AWAITING_SHIPMENT".equalsIgnoreCase(String.valueOf(status));
    }
}
