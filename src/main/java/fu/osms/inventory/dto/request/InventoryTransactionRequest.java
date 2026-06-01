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

    @NotNull(message = "Shop ID không được để trống")
    private UUID shopId;

    @NotNull(message = "Warehouse ID không được để trống")
    private UUID warehouseId;

    @NotNull(message = "Variant ID không được để trống")
    private UUID variantId;

    @NotNull(message = "Loại giao dịch không được để trống")
    private InvTxnType type;

    private String referenceType;

    private UUID referenceId;

    @NotNull(message = "Số lượng thay đổi không được để trống")
    private Integer quantityChange;

    private String note;
}
