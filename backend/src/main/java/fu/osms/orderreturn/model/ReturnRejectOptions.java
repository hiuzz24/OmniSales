package fu.osms.orderreturn.model;

import java.util.List;

public record ReturnRejectOptions(
        boolean requiresReasonCode,
        boolean allowsComment,
        List<Option> options,
        String unavailableReason
) {
    public ReturnRejectOptions {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public record Option(String code, String label) {
    }
}
