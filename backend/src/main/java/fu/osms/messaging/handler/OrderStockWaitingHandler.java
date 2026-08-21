package fu.osms.messaging.handler;

import fu.osms.messaging.dto.OrderStockWaitingMessage;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderStockAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStockWaitingHandler {
    private static final int BATCH_SIZE = 50;

    private final OrderRepository orderRepository;
    private final OrderStockAllocationService allocationService;

    /** Cập nhật khả năng xác nhận, không tự động phân bổ tồn theo FIFO. */
    public void handle(OrderStockWaitingMessage message) {
        if (message == null || message.variantIds() == null || message.variantIds().isEmpty()) return;

        int offset = 0;
        List<UUID> orderIds;
        do {
            orderIds = orderRepository.findWaitingStockCandidateIds(message.variantIds(), BATCH_SIZE, offset);
            for (UUID orderId : orderIds) {
                try {
                    allocationService.refreshWaitingStockAvailability(orderId);
                } catch (Exception exception) {
                    log.warn("[OrderStockWaiting] Readiness refresh failed messageId={} orderId={}",
                            message.messageId(), orderId, exception);
                    throw exception;
                }
            }
            offset += orderIds.size();
        } while (orderIds.size() == BATCH_SIZE);
    }
}
