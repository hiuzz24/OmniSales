package fu.osms.order.dto.request;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.enums.OrderStatus;
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

    @NotNull(message = "Shop ID không được để trống")
    private UUID shopId;

    private UUID channelId;

    @NotNull(message = "Platform không được để trống")
    private PlatformType platform;

    @NotBlank(message = "Tên kênh không được để trống")
    @Size(max = 100)
    private String channelName;

    @NotBlank(message = "External Order ID không được để trống")
    @Size(max = 200)
    private String externalOrderId;

    private OrderStatus status = OrderStatus.PENDING;

    private String paymentStatus = "UNPAID";

    @Size(max = 255)
    private String buyerName;

    @Size(max = 50)
    private String buyerPhone;

    @NotNull(message = "Địa chỉ giao hàng không được để trống")
    private Map<String, Object> shippingAddress;

    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Size(max = 3)
    private String currency = "VND";

    private String note;

    @Size(max = 200)
    private String trackingNumber;

    @NotEmpty(message = "Đơn hàng phải có ít nhất một sản phẩm")
    private List<OrderItemRequest> items;
}
