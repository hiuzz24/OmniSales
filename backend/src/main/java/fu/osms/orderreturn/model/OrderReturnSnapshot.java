package fu.osms.orderreturn.model;

import fu.osms.orderreturn.enums.OrderReturnStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record OrderReturnSnapshot(
        String externalReturnId,
        String externalOrderId,
        String platformStatus,
        OrderReturnStatus status,
        OffsetDateTime platformUpdatedAt,
        String webhookEventId,
        boolean refundOnly,
        boolean refundConfirmed,
        List<Item> items,
        Map<String, Object> metadata
) {
    public record Item(
            String externalReturnItemId,
            String externalOrderItemId,
            String sku,
            String name,
            int requestedQuantity,
            int approvedQuantity,
            Integer refundedQuantity
    ) {
    }
}
