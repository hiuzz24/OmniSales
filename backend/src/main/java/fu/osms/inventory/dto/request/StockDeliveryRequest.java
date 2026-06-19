package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeliveryRequest {

    @NotNull(message = "Warehouse ID is required")
    private UUID warehouseId;

    private UUID orderId; // Nullable - only for ORDER type

    @NotNull(message = "Delivery type is required")
    @Pattern(regexp = "ORDER|ADJUSTMENT|DISPOSAL|TRANSFER",
            message = "Delivery type must be ORDER, ADJUSTMENT, DISPOSAL, or TRANSFER")
    private String deliveryType; // ORDER, ADJUSTMENT, DISPOSAL, TRANSFER

    private String note;
    private String notes;
    private String recipient;

    @NotNull(message = "Issue date is required")
    private LocalDate issuedDate;

    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<StockDeliveryItemRequest> items;
}
