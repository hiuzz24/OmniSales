package fu.osms.inventory.dto.request;

import fu.osms.inventory.enums.InvTxnType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionRequest {

    @NotNull(message = "Shop ID must not be null")

    @NotNull(message = "Warehouse ID must not be null")
    private UUID warehouseId;

    @NotNull(message = "Variant ID must not be null")
    private UUID variantId;

    @NotNull(message = "Transaction type must not be null")
    private InvTxnType type;

    private String referenceType;

    private UUID referenceId;

    @NotNull(message = "Quantity change must not be null")
    private Integer quantityChange;

    private String note;
}
