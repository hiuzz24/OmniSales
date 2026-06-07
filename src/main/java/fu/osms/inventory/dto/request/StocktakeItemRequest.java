package fu.osms.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeItemRequest {

    @NotNull(message = "Variant ID must not be null")
    private UUID variantId;

    @NotNull(message = "System quantity must not be null")
    private Integer systemQuantity;

    @NotNull(message = "Actual quantity must not be null")
    private Integer actualQuantity;

    private String notes;
}
