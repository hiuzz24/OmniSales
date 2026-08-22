package fu.osms.order.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBuyerCancellationRequest(
        @NotBlank String reasonCode,
        @Size(max = 500) String comment
) {
}
