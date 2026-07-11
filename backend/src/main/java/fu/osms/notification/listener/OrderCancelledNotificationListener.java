package fu.osms.notification.listener;

import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledNotificationListener {

    private final NotificationService notificationService;
    private final UserRoleRepository userRoleRepository;

    @EventListener
    public void handleOrderCancelledEvent(OrderCancelledEvent event) {
        Order order = event.getOrder();
        if (order == null || order.getId() == null) {
            return;
        }

        String reason = order.getCancelReason() != null ? order.getCancelReason() : "Không có lý do cụ thể";
        String title = "Đơn hàng đã hủy";
        String body = "Đơn hàng " + order.getExternalOrderId() + " đã bị hủy. Lý do: " + reason;

        userRoleRepository.findByRoleNameIn(List.of("OWNER", "OPERATIONS")).stream()
                .map(userRole -> userRole.getUser().getId())
                .distinct()
                .forEach(userId -> {
                    try {
                        notificationService.createNotification(
                                userId,
                                "ORDER_CANCELLED",
                                title,
                                body,
                                "ORDER",
                                order.getId()
                        );
                    } catch (Exception e) {
                        log.error("Failed to send order cancelled notification to user: {}", userId, e);
                    }
                });
    }
}
