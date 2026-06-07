package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReceiptRequest {

    @NotNull(message = "Shop ID must not be null")
    private UUID shopId;

    @NotNull(message = "Warehouse ID must not be null")
    private UUID warehouseId;

    private UUID supplierId;

    @NotBlank(message = "Receipt code must not be blank")
    @Size(max = 100)
    private String receiptCode;

    @Size(max = 100)
    private String invoiceNumber;

    private String notes;

    @NotEmpty(message = "Receipt must have at least one item")
    @Valid
    private List<InventoryReceiptItemRequest> items;
}
