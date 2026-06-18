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

    // For DRAFT: can be null
    // For CONFIRM: will be validated in service layer
    private Integer quantity;

    // For DRAFT: can be null
    // For CONFIRM: will be validated in service layer
    private BigDecimal unitCost;

    private String notes;
}
