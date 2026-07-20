package fu.osms.order.dto.response;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.enums.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private UUID id;
    private UUID channelId;
    private UUID customerId;
    private String customerName;
    private PlatformType platform;
    private String channelName;
    private String externalOrderId;
    private OrderStatus status;
    private String paymentStatus;
    private OffsetDateTime statusChangedAt;
    private String buyerName;
    private String buyerPhone;
    private Map<String, Object> shippingAddress;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String currency;
    private String note;
    private String trackingNumber;
    private UUID cancelledById;
    private String cancelledByName;
    private String cancelReason;
    private Map<String, Object> platformMetadata;
    private Long version;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<OrderItemResponse> items;
}
