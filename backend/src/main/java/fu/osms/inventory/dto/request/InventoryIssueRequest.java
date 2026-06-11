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
public class InventoryIssueRequest {

    @NotNull(message = "Shop ID must not be null")

    @NotNull(message = "Warehouse ID must not be null")
    private UUID warehouseId;

    @NotBlank(message = "Issue code must not be blank")
    @Size(max = 100)
    private String issueCode;

    @NotBlank(message = "Issue type must not be blank")
    @Size(max = 20)
    private String issueType;

    private UUID referenceId;

    private String notes;

    @NotEmpty(message = "Issue must have at least one item")
    @Valid
    private List<InventoryIssueItemRequest> items;
}
