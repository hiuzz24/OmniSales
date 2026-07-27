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

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderFulfillmentNotificationListener {

    private static final String TYPE = "ORDER_PICK_REQUIRED";

    private final OrderRepository orderRepository;
    private final OrderWorkflowNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.previousStatus() == OrderStatus.PROCESSING
                || event.currentStatus() != OrderStatus.PROCESSING) {
            return;
        }

        Order order = orderRepository.findById(event.orderId()).orElse(null);
        if (order == null) {
            return;
        }
        String orderCode = order.getExternalOrderId() != null
                ? order.getExternalOrderId()
                : order.getId().toString();
        try {
            notificationService.notifyRoles(
                    List.of("OWNER", "OPERATIONS"),
                    TYPE,
                    "Đơn hàng cần tạo phiếu xuất",
                    "Đơn hàng " + orderCode + " đã chuyển sang Đang xử lý.",
                    "ORDER",
                    order.getId()
            );
        } catch (Exception exception) {
            log.warn("Could not notify fulfillment roles for orderId={}: {}",
                    event.orderId(), exception.getMessage());
        }
    }
}
