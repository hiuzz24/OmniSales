package fu.osms.inventory.dto.response;

import java.util.UUID;

public record OrderStockDeliveryCandidateItemResponse(
        UUID orderItemId,
        UUID variantId,
        String sku,
        String name,
        Integer quantity
) {
}
