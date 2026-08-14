package fu.osms.orderreturn.service.impl;

import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.PaymentStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.orderreturn.service.OrderReturnPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReturnPaymentServiceImpl implements OrderReturnPaymentService {

    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository returnItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /** Cập nhật Order.paymentStatus khi platform xác nhận hoàn đủ; OSMS không trực tiếp hoàn tiền. */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void projectPayment(UUID returnId) {
        OrderReturn orderReturn = returnRepository.findForUpdateById(returnId).orElse(null);
        if (orderReturn == null || orderReturn.getRefundConfirmedAt() == null) {
            return;
        }
        Order order = orderRepository.findForUpdateById(orderReturn.getOrder().getId()).orElse(null);
        if (order == null) {
            return;
        }
        if (PaymentStatus.REFUNDED.name().equals(order.getPaymentStatus())) {
            return;
        }
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        List<OrderReturnItem> confirmed = returnItemRepository.findValidItemsByOrderId(order.getId()).stream()
                .filter(item -> item.getOrderReturn().getRefundConfirmedAt() != null)
                .toList();
        Map<UUID, Integer> refundedByItem = new HashMap<>();
        for (OrderReturnItem item : confirmed) {
            if (item.getOrderItem() == null || item.getRefundedQuantity() == null) continue;
            refundedByItem.merge(item.getOrderItem().getId(), item.getRefundedQuantity(), Integer::sum);
        }
        boolean fullyRefunded = !orderItems.isEmpty();
        for (OrderItem item : orderItems) {
            int refunded = refundedByItem.getOrDefault(item.getId(), 0);
            if (refunded > item.getQuantity()) {
                log.warn("[OrderReturn] Refund quantity exceeds order item orderId={} orderItemId={} refunded={} ordered={}",
                        order.getId(), item.getId(), refunded, item.getQuantity());
            }
            if (Math.min(refunded, item.getQuantity()) < item.getQuantity()) {
                fullyRefunded = false;
            }
        }
        if (fullyRefunded) {
            order.setPaymentStatus(PaymentStatus.REFUNDED.name());
            orderRepository.save(order);
        }
    }
}
