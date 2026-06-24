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
@ToString
public class StockTransferItemRequest {

    @NotNull(message = "Variant ID must not be null")
    private UUID variantId;

    @Min(value = 1, message = "Quantity must be greater than 0")
    private Integer quantity;

    @NotNull(message = "Unit cost must not be null")
    @DecimalMin(value = "0", message = "Unit cost must not be negative")
    private BigDecimal unitCost;


}
