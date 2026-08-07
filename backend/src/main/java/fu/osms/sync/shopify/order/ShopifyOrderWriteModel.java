package fu.osms.sync.shopify.order;

import fu.osms.order.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ShopifyOrderWriteModel(
        String externalOrderId, OffsetDateTime createdAt, OrderStatus status, String paymentStatus, String buyerName, String buyerPhone,
        Map<String, Object> shippingAddress, BigDecimal subtotal, BigDecimal discountAmount,
        BigDecimal shippingFee, String currency, String note, String trackingNumber, String cancelReason,
        List<Item> items
) {
    public record Item(String externalItemId, String externalVariantId, String sku, String name, int quantity,
                       BigDecimal unitPrice, BigDecimal discountAmount) {
    }
}
