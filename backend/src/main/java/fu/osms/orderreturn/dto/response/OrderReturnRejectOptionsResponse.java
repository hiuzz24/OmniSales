package fu.osms.orderreturn.dto.response;

import java.util.List;

public record OrderReturnRejectOptionsResponse(
        boolean requiresReasonCode,
        boolean allowsComment,
        List<Option> options,
        String unavailableReason
) {
    public record Option(String code, String label) {
    }
}
