package fu.osms.inventory.dto.response;

import fu.osms.common.enums.PlatformType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record OrderStockDeliveryCandidateResponse(
        UUID orderId,
        String externalOrderId,
        PlatformType platform,
        String channelName,
        String buyerName,
        String buyerPhone,
        OffsetDateTime createdAt,
        Integer totalQuantity,
        List<OrderStockDeliveryCandidateItemResponse> items
) {
}
