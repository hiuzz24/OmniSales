package fu.osms.inventory.dto.response;

import java.util.List;

public record OrderStockDeliveryBatchResponse(
        int successCount,
        int failCount,
        List<OrderStockDeliveryBatchItemResponse> results
) {
}
