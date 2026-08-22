package fu.osms.order.dto.response;

import java.util.List;

public record BuyerCancellationResponse(
        String cancelId,
        String cancelStatus,
        boolean active,
        boolean sellerActionRequired,
        String sellerNextAction,
        String actionState,
        Decision approve,
        RejectDecision reject
) {
    public record Decision(boolean eligible, String warning) {
    }

    public record RejectDecision(boolean eligible, String warning, List<Reason> reasons) {
    }

    public record Reason(String code, String label) {
    }
}
