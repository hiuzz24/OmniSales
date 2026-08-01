package fu.osms.orderreturn.dto.request;

import jakarta.validation.constraints.Size;

public record OrderReturnRejectRequest(
        @Size(max = 200) String reasonCode,
        @Size(max = 500) String comment,
        @Size(max = 500) String reason
) {
}
