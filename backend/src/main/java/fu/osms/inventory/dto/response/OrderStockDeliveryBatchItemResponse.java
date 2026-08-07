package fu.osms.inventory.dto.response;

import java.util.UUID;

public record OrderStockDeliveryBatchItemResponse(
        UUID orderId,
        String status,
        UUID stockDeliveryId,
        String issueCode,
        String message
) {
    public static OrderStockDeliveryBatchItemResponse created(UUID orderId, StockDeliveryResponse response) {
        return new OrderStockDeliveryBatchItemResponse(
                orderId, "CREATED", response.getId(), response.getIssueCode(), null);
    }

    public static OrderStockDeliveryBatchItemResponse failed(UUID orderId, String message) {
        return new OrderStockDeliveryBatchItemResponse(orderId, "FAILED", null, null, message);
    }
}
