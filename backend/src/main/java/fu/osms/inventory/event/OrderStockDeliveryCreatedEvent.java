package fu.osms.inventory.event;

import java.util.UUID;

public record OrderStockDeliveryCreatedEvent(
        UUID orderId,
        UUID stockDeliveryId,
        String issueCode
) {
}
