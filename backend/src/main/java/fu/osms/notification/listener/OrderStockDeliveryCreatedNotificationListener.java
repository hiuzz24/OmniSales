package fu.osms.notification.listener;

import fu.osms.inventory.event.OrderStockDeliveryCreatedEvent;
import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.order.entity.Order;
import fu.osms.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderStockDeliveryCreatedNotificationListener {

    private static final String TYPE = "ORDER_READY_SHIP";

    private final OrderRepository orderRepository;
    private final OrderWorkflowNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockDeliveryCreated(OrderStockDeliveryCreatedEvent event) {
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            return;
        }
        String orderCode = order.getExternalOrderId() != null
                ? order.getExternalOrderId()
                : order.getId().toString();
        try {
            notificationService.notifyRoles(
                    List.of("OWNER", "SALES"),
                    TYPE,
                    "Đơn hàng đã sẵn sàng giao",
                    "Phiếu " + event.issueCode() + " đã được tạo cho đơn hàng " + orderCode + ".",
                    "ORDER",
                    order.getId()
            );
        } catch (Exception exception) {
            log.warn("Could not notify sales roles for orderId={}, stockDeliveryId={}: {}",
                    event.orderId(), event.stockDeliveryId(), exception.getMessage());
        }
    }
}
