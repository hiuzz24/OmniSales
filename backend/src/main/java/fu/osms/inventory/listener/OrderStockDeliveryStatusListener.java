package fu.osms.inventory.listener;

import fu.osms.inventory.service.OrderStockDeliveryService;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.event.OrderStatusChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStockDeliveryStatusListener {

    private final OrderStockDeliveryService orderStockDeliveryService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderStatusChangedEvent event) {
        try {
            if (event.currentStatus() == OrderStatus.IN_TRANSIT
                    || event.currentStatus() == OrderStatus.DELIVERED) {
                orderStockDeliveryService.completeForOrder(event.orderId());
            } else if (event.currentStatus() == OrderStatus.CANCELLED) {
                orderStockDeliveryService.cancelDraftForOrder(event.orderId());
            }
        } catch (Exception exception) {
            log.error("[OrderStockDelivery] Lifecycle update failed for orderId={}, status={}",
                    event.orderId(), event.currentStatus(), exception);
        }
    }
}
