package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReceiveItemRequest {

    @NotNull(message = "Variant ID must not be null")
    private UUID variantId;


    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer quantity;

    @DecimalMin(value = "0.0", inclusive = true, message = "Đơn giá phải lớn hơn hoặc bằng 0")
    private BigDecimal unitCost;

    private String notes;
}
