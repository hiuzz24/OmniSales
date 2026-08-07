package fu.osms.orderreturn.service.impl;

import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class OrderReturnValidationPolicy {

    private final OrderReturnItemRepository returnItemRepository;

    OrderReturnValidationPolicy(OrderReturnItemRepository returnItemRepository) {
        this.returnItemRepository = returnItemRepository;
    }

    String cumulativeError(Order order, OrderReturn current,
                           List<OrderReturnItem> incoming, List<OrderItem> orderItems) {
        Map<UUID, Integer> returned = new HashMap<>();
        returnItemRepository.findValidItemsByOrderId(order.getId()).stream()
                .filter(item -> !item.getOrderReturn().getId().equals(current.getId()))
                .filter(item -> item.getOrderItem() != null)
                .forEach(item -> returned.merge(item.getOrderItem().getId(),
                        item.getApprovedQuantity(), Integer::sum));
        incoming.forEach(item -> returned.merge(item.getOrderItem().getId(),
                item.getApprovedQuantity(), Integer::sum));
        for (OrderItem orderItem : orderItems) {
            if (returned.getOrDefault(orderItem.getId(), 0) > orderItem.getQuantity()) {
                return "RETURN_QUANTITY_EXCEEDS_ORDER_ITEM: "
                        + fallback(orderItem.getSku(), orderItem.getId().toString());
            }
        }
        return null;
    }

    boolean refundCoversReceivedQuantities(List<OrderReturnItem> items) {
        List<OrderReturnItem> received = items.stream()
                .filter(item -> item.getReceivedQuantity() != null && item.getReceivedQuantity() > 0)
                .toList();
        return !received.isEmpty() && received.stream().allMatch(item -> item.getRefundedQuantity() != null
                && item.getRefundedQuantity() >= item.getReceivedQuantity());
    }

    private String fallback(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
