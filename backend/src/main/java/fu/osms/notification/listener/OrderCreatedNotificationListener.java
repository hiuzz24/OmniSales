package fu.osms.notification.listener;

import fu.osms.auth.repository.UserRoleRepository;
import fu.osms.notification.service.NotificationService;
import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedNotificationListener {

    private final NotificationService notificationService;
    private final UserRoleRepository userRoleRepository;

    @EventListener
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        Order order = event.getOrder();
        if (order == null || order.getId() == null) {
            return;
        }

        String channelName = order.getChannelName() != null ? order.getChannelName() : "Hệ thống";
        String title = "Có đơn hàng mới";
        String body = "Đơn hàng từ " + channelName + " với mã " + order.getExternalOrderId() + " đã được tạo thành công.";

        userRoleRepository.findByRoleNameIn(List.of("OWNER", "OPERATIONS")).stream()
                .map(userRole -> userRole.getUser().getId())
                .distinct()
                .forEach(userId -> {
                    try {
                        notificationService.createNotification(
                                userId,
                                "ORDER_NEW",
                                title,
                                body,
                                "ORDER",
                                order.getId()
                        );
                    } catch (Exception e) {
                        log.error("Failed to send order created notification to user: {}", userId, e);
                    }
                });
    }
}
