package fu.osms.inventory.dto.response;

import fu.osms.common.enums.PlatformType;

import java.math.BigDecimal;
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
        BigDecimal totalAmount,
        String currency,
        Integer totalQuantity,
        List<OrderStockDeliveryCandidateItemResponse> items
) {
}
