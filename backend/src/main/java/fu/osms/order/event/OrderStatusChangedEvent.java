package fu.osms.order.event;

import fu.osms.order.enums.OrderStatus;

import java.util.UUID;

public record OrderStatusChangedEvent(
        UUID orderId,
        OrderStatus previousStatus,
        OrderStatus currentStatus
) {
}
