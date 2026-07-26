package fu.osms.inventory.dto.response;

import java.util.UUID;

public record OrderStockDeliveryReadinessResponse(
        UUID orderId,
        boolean readyForShipment,
        UUID stockDeliveryId,
        String issueCode,
        String status
) {
}
