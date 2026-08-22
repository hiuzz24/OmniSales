package fu.osms.sync.tiktok.order;

import fu.osms.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record TikTokOrderWriteModel(
        String externalOrderId, OffsetDateTime createdAt, OrderStatus status, String rawStatus, String paymentStatus,
        String buyerName, String buyerPhone, Map<String, Object> shippingAddress,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal shippingFee, String currency,
        String note, String cancelReason, String trackingNumber, Long updateTime,
        OffsetDateTime shippingDueTime, OffsetDateTime collectionDueTime,
        OffsetDateTime rtsSlaTime, OffsetDateTime ttsSlaTime,
        Integer fulfillmentPriorityLevel, String shippingType, Boolean preOrder,
        Map<String, Object> detailMetadata, List<Item> items
) {
    public record Item(String externalItemId, String externalVariantId, String sku, String name, int quantity,
                       BigDecimal unitPrice, BigDecimal discountAmount) {
    }
}
