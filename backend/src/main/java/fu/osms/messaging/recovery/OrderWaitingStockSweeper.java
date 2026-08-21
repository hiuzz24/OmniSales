package fu.osms.messaging.recovery;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderStockWaitingMessage;
import fu.osms.messaging.handler.OrderStockWaitingHandler;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.impl.OrderWaitingStockMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderWaitingStockSweeper {
    private static final int BATCH_SIZE = 50;

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderWaitingStockMaintenanceService maintenanceService;
    private final EventPublisher eventPublisher;
    private final OrderStockWaitingHandler handler;

    @Scheduled(fixedDelayString = "${osms.order.waiting-stock-sweeper-delay-ms:60000}")
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID orderId : orderRepository.findExpiredWaitingStockIds(now, BATCH_SIZE)) {
            try {
                maintenanceService.markExpired(orderId, now);
            } catch (Exception exception) {
                log.warn("[OrderWaitingStockSweeper] Could not mark expired orderId={}", orderId, exception);
            }
        }
        publishReconcile(new LinkedHashSet<>(
                orderItemRepository.findActiveWaitingStockVariantIds(now, BATCH_SIZE)));
    }

    private void publishReconcile(Set<UUID> variantIds) {
        if (variantIds.isEmpty()) return;
        OrderStockWaitingMessage message = new OrderStockWaitingMessage(UUID.randomUUID(), variantIds);
        eventPublisher.publish(RabbitMQConstants.ORDER_STOCK_WAITING_RECONCILE, message,
                () -> handler.handle(message));
    }
}
