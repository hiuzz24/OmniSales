package fu.osms.order.dto.response;

import java.util.UUID;

public record WaitingStockItemResponse(
        UUID variantId,
        String sku,
        String name,
        int required,
        int available,
        int missing
) {
}
