package fu.osms.notification.listener;

import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderLifecycleNotificationListener {

    private final OrderRepository orderRepository;
    private final OrderWorkflowNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStatusChanged(OrderStatusChangedEvent event) {
        if (event.currentStatus() != OrderStatus.SHIPPED
                && event.currentStatus() != OrderStatus.DELIVERED) {
            return;
        }
        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) return;

        String code = order.getExternalOrderId() != null
                ? order.getExternalOrderId() : order.getId().toString();
        boolean delivered = event.currentStatus() == OrderStatus.DELIVERED;
        try {
            notificationService.notifyRolesOnce(
                    List.of("OWNER", "OPERATIONS", "SALES"),
                    delivered ? "ORDER_DELIVERED" : "ORDER_SHIPPED",
                    delivered ? "Đơn hàng đã giao thành công" : "Đơn hàng đã bàn giao vận chuyển",
                    "Đơn hàng " + code + (delivered
                            ? " đã được giao thành công."
                            : " đã được bàn giao cho đơn vị vận chuyển."),
                    "ORDER",
                    order.getId());
        } catch (Exception exception) {
            log.warn("Could not notify order lifecycle orderId={}: {}",
                    event.orderId(), exception.getMessage());
        }
    }
}
