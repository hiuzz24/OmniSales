package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

import java.util.List;
import java.util.UUID;

public record OrderStockDeliveryBatchRequest(
        List<UUID> orderIds,
        List<@Valid OrderStockDeliveryCreateRequest> orders
) {
    @AssertTrue(message = "Chỉ gửi orderIds hoặc orders và phải chọn ít nhất một đơn hàng")
    public boolean hasExactlyOneRequestFormat() {
        boolean hasLegacyOrders = orderIds != null && !orderIds.isEmpty();
        boolean hasOrdersWithGifts = orders != null && !orders.isEmpty();
        return hasLegacyOrders ^ hasOrdersWithGifts;
    }

    public List<OrderStockDeliveryCreateRequest> normalizedOrders() {
        if (orders != null && !orders.isEmpty()) {
            return List.copyOf(orders);
        }
        if (orderIds == null) {
            return List.of();
        }
        return orderIds.stream()
                .map(orderId -> new OrderStockDeliveryCreateRequest(orderId, List.of()))
                .toList();
    }
}
