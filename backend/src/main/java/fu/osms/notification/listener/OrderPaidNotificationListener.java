package fu.osms.notification.listener;

import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPaidNotificationListener {

    private final NotificationService notificationService;
    private final UserRoleRepository userRoleRepository;

    @EventListener
    public void handleOrderPaidEvent(OrderPaidEvent event) {
        Order order = event.getOrder();
        if (order == null || order.getId() == null) {
            return;
        }

        String title = "Đơn hàng đã thanh toán";
        String body = "Đơn hàng " + order.getExternalOrderId() + " đã được thanh toán thành công.";

        userRoleRepository.findByRoleNameIn(List.of("OWNER", "SALES")).stream()
                .map(userRole -> userRole.getUser().getId())
                .distinct()
                .forEach(userId -> {
                    try {
                        notificationService.createNotification(
                                userId,
                                "ORDER",
                                title,
                                body,
                                "ORDER",
                                order.getId()
                        );
                    } catch (Exception e) {
                        log.error("Failed to send order paid notification to user: {}", userId, e);
                    }
                });
    }
}
