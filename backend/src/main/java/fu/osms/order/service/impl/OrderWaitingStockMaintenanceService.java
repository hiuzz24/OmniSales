package fu.osms.order.service.impl;

import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderWaitingStockMaintenanceService {
    private final OrderRepository orderRepository;
    private final OrderWorkflowNotificationService notificationService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markExpired(UUID orderId, OffsetDateTime now) {
        Order order = orderRepository.findForUpdateById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.WAITING_STOCK
                || order.getWaitingStockExpiresAt() == null
                || order.getWaitingStockExpiresAt().isAfter(now)
                || order.getWaitingStockExpiryNotifiedAt() != null) return false;
        order.setWaitingStockExpiryNotifiedAt(now);
        orderRepository.save(order);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notificationService.notifyRolesOnce(
                        List.of("OWNER", "SALES"),
                        "ORDER_WAITING_STOCK_EXPIRED",
                        "Đơn chờ hàng đã quá hạn cấp tồn",
                        "Đơn " + order.getExternalOrderId()
                                + " đã quá hạn. Vui lòng kiểm tra và quyết định hủy đơn.",
                        "ORDER",
                        order.getId());
            }
        });
        return true;
    }
}
