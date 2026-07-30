package fu.osms.orderreturn.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderReturnItemResponse(
        UUID id,
        UUID orderItemId,
        String externalOrderItemId,
        String externalReturnItemId,
        String sku,
        String name,
        int requestedQuantity,
        int approvedQuantity,
        Integer receivedQuantity,
        Integer restockableQuantity,
        Integer damagedQuantity,
        Integer missingQuantity,
        Integer refundedQuantity,
        BigDecimal unitPrice
) {
}
