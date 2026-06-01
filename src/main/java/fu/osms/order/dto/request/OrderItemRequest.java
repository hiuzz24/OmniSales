package fu.osms.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemRequest {

    private UUID variantId;

    private UUID channelVariantId;

    @Size(max = 100)
    private String sku;

    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(max = 500)
    private String name;

    @NotNull(message = "Số lượng không được để trống")
    @Positive(message = "Số lượng phải lớn hơn 0")
    private Integer quantity;

    @NotNull(message = "Đơn giá không được để trống")
    private BigDecimal unitPrice;

    private BigDecimal discountAmount = BigDecimal.ZERO;
}
