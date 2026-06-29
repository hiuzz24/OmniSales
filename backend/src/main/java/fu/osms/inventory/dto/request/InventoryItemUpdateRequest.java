package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemUpdateRequest {

    @NotBlank(message = "Product name must not be blank")
    private String productVariantName;

    @NotNull(message = "Price must not be null")
    @Min(value = 0, message = "Price must not be negative")
    private BigDecimal price;

    @NotNull(message = "Average cost must not be null")
    @Min(value = 0, message = "Average cost must not be negative")
    private BigDecimal averageCost;

    @NotNull(message = "Quantity on hand must not be null")
    @Min(value = 0, message = "Quantity on hand must not be negative")
    private Integer quantityOnHand;

    @NotNull(message = "Warehouse ID must not be null")
    private UUID warehouseId;
}
