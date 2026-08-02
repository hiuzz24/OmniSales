package fu.osms.orderreturn.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderReturnInspectionRequest(
        @NotEmpty List<@Valid Item> items
) {
    public record Item(
            @NotNull UUID returnItemId,
            @Min(0) int receivedQuantity,
            @Min(0) int restockableQuantity,
            @Min(0) int damagedQuantity,
            @Min(0) int missingQuantity
    ) {
    }
}
