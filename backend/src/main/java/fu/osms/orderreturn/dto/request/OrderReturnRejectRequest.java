package fu.osms.orderreturn.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrderReturnRejectRequest(@NotBlank String reason) {
}
