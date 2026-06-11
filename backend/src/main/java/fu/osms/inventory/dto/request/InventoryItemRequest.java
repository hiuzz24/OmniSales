package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemRequest {

    @NotNull(message = "Shop ID must not be null")

    @NotNull(message = "Warehouse ID must not be null")
    private UUID warehouseId;

    @NotNull(message = "Variant ID must not be null")
    private UUID variantId;

    @Min(value = 0, message = "Quantity on hand must not be negative")
    @Builder.Default
    private Integer quantityOnHand = 0;

    @Min(value = 0, message = "Reserved quantity must not be negative")
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Min(value = 0, message = "Low stock threshold must not be negative")
    @Builder.Default
    private Integer lowStockThreshold = 5;
}
