package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class StockTransferRequest {

    @NotNull(message = "Shop ID must not be null")

    @NotNull(message = "Source warehouse must not be null")
    private UUID fromWarehouseId;

    private UUID createdById;
    @NotNull(message = "Destination warehouse must not be null")
    private UUID toWarehouseId;

    @NotBlank(message = "Transfer code must not be blank")
    @Size(max = 100)
    private String transferCode;
    @NotNull(message = "Transfer time must not be null")
    private OffsetDateTime transferTime;

    @NotEmpty(message = "Transfer must have at least one item")
    @Valid
    private List<StockTransferItemRequest> items;

    private String note;
    private String status;
}
