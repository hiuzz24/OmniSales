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

    @NotNull(message = "Shop ID không được để trống")
    private UUID shopId;

    @NotNull(message = "Warehouse ID không được để trống")
    private UUID warehouseId;

    @NotNull(message = "Variant ID không được để trống")
    private UUID variantId;

    @Min(value = 0, message = "Số lượng trong kho không được âm")
    private Integer quantityOnHand = 0;

    @Min(value = 0, message = "Số lượng đặt trước không được âm")
    private Integer reservedQuantity = 0;

    @Min(value = 0, message = "Ngưỡng tồn kho thấp không được âm")
    private Integer lowStockThreshold = 5;
}
