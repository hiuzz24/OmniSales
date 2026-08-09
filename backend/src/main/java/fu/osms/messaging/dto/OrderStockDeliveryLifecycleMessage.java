package fu.osms.messaging.dto;

import fu.osms.order.enums.OrderStatus;

import java.util.UUID;

public record OrderStockDeliveryLifecycleMessage(
        UUID messageId,
        String actionKey,
        UUID orderId,
        OrderStatus status
) {
}
