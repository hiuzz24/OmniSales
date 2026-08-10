package fu.osms.messaging.recovery;

import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderStockDeliveryLifecycleMessage;
import fu.osms.messaging.handler.OrderStockDeliveryLifecycleHandler;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStockDeliveryRecoverySweeper {

    private static final long CACHE_MINUTES = 10;

    private final InventoryIssueRepository issueRepository;
    private final OrderRepository orderRepository;
    private final EventPublisher eventPublisher;
    private final OrderStockDeliveryLifecycleHandler handler;
    private final Map<String, OffsetDateTime> recentlyPublished = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${app.messaging.recovery.order-stock-delivery-delay-ms:60000}")
    public void sweep() {
        OffsetDateTime now = OffsetDateTime.now();
        recentlyPublished.entrySet().removeIf(entry -> entry.getValue().isBefore(now.minusMinutes(CACHE_MINUTES)));
        for (InventoryIssue issue : issueRepository.findOrderLifecycleRecoveryCandidates(
                java.util.List.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
                InvTxnType.OUTBOUND,
                PageRequest.of(0, 50))) {
            if (issue.getReferenceId() == null) {
                continue;
            }
            Order order = orderRepository.findById(issue.getReferenceId()).orElse(null);
            if (order == null || !isRecoverable(order.getStatus())) {
                continue;
            }
            String action = order.getStatus() == OrderStatus.CANCELLED ? "CANCEL" : "COMPLETE";
            String actionKey = "ORDER_STOCK_DELIVERY:" + order.getId() + ":" + action;
            if (recentlyPublished.putIfAbsent(actionKey, now) != null) {
                continue;
            }
            OrderStockDeliveryLifecycleMessage message = new OrderStockDeliveryLifecycleMessage(
                    UUID.randomUUID(), actionKey, order.getId(), order.getStatus());
            try {
                eventPublisher.publish(
                        RabbitMQConstants.ORDER_STOCK_DELIVERY_LIFECYCLE,
                        message,
                        () -> handler.handle(message));
            } catch (Exception exception) {
                recentlyPublished.remove(actionKey);
                log.warn("[OrderStockDeliveryRecovery] Publish failed actionKey={}", actionKey, exception);
            }
        }
    }

    private boolean isRecoverable(OrderStatus status) {
        return status == OrderStatus.DELIVERED
                || status == OrderStatus.CANCELLED;
    }
}
