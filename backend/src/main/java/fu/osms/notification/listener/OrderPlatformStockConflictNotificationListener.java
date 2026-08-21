package fu.osms.notification.listener;

import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.order.event.OrderPlatformStockConflictEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderPlatformStockConflictNotificationListener {
    private final OrderWorkflowNotificationService notificationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConflict(OrderPlatformStockConflictEvent event) {
        notificationService.notifyRolesOnce(
                List.of("OWNER", "SALES", "OPERATIONS"),
                "ORDER_PLATFORM_STOCK_CONFLICT",
                "Sàn đã xử lý đơn ngoài OSMS",
                "Đơn chờ hàng đã chuyển trên sàn sang " + event.platformStatus()
                        + ". Cần đối soát tồn kho thủ công.",
                "ORDER",
                event.orderId());
    }
}
