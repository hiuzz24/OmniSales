package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeSessionRequest {

    @NotNull(message = "Shop ID must not be null")
    private UUID shopId;

    @NotNull(message = "Warehouse ID must not be null")
    private UUID warehouseId;

    @NotBlank(message = "Session code must not be blank")
    @Size(max = 100)
    private String sessionCode;

    private LocalDate scheduledDate;

    @Valid
    private List<StocktakeItemRequest> items;
}
