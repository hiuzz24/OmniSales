package fu.osms.order.dto.request;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.enums.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    private UUID channelId;

    private UUID customerId;

    @NotNull(message = "Platform is required")
    private PlatformType platform;

    @NotBlank(message = "Channel name is required")
    @Size(max = 100)
    private String channelName;

    @NotBlank(message = "External order ID is required")
    @Size(max = 200)
    private String externalOrderId;

    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    @Size(max = 20)
    @Builder.Default
    private String paymentStatus = "UNPAID";

    @Size(max = 255)
    private String buyerName;

    @Size(max = 50)
    private String buyerPhone;

    @NotNull(message = "Shipping address is required")
    private Map<String, Object> shippingAddress;

    @NotNull(message = "Subtotal is required")
    private BigDecimal subtotal;

    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Size(max = 3)
    @Builder.Default
    private String currency = "VND";

    private String note;

    @Size(max = 200)
    private String trackingNumber;

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;
}
