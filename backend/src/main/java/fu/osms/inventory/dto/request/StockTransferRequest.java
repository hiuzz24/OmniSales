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
public class StockTransferRequest {

    @NotNull(message = "Shop ID must not be null")
    private UUID shopId;

    @NotNull(message = "Source warehouse must not be null")
    private UUID fromWarehouseId;

    @NotNull(message = "Destination warehouse must not be null")
    private UUID toWarehouseId;

    @NotBlank(message = "Transfer code must not be blank")
    @Size(max = 100)
    private String transferCode;

    @NotEmpty(message = "Transfer must have at least one item")
    @Valid
    private List<StockTransferItemRequest> items;
}
