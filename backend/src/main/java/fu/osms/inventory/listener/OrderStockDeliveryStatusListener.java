package fu.osms.inventory.listener;

import fu.osms.messaging.constants.RabbitMQConstants;
import fu.osms.messaging.dto.OrderStockDeliveryLifecycleMessage;
import fu.osms.messaging.handler.OrderStockDeliveryLifecycleHandler;
import fu.osms.messaging.publisher.EventPublisher;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderStockDeliveryStatusListener {

    private final EventPublisher eventPublisher;
    private final OrderStockDeliveryLifecycleHandler handler;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderStatusChangedEvent event) {
        if (event.currentStatus() == OrderStatus.DELIVERED
                || event.currentStatus() == OrderStatus.CANCELLED) {
            String action = event.currentStatus() == OrderStatus.CANCELLED ? "CANCEL" : "COMPLETE";
            OrderStockDeliveryLifecycleMessage message = new OrderStockDeliveryLifecycleMessage(
                    UUID.randomUUID(),
                    "ORDER_STOCK_DELIVERY:" + event.orderId() + ":" + action,
                    event.orderId(),
                    event.currentStatus());
            eventPublisher.publish(
                    RabbitMQConstants.ORDER_STOCK_DELIVERY_LIFECYCLE,
                    message,
                    () -> handler.handle(message));
        }
    }
}
