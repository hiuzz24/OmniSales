package fu.osms.inventory.dto.response;

import fu.osms.inventory.enums.ReservationResult;
import fu.osms.order.dto.response.WaitingStockItemResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ReservationOutcome(
        ReservationResult result,
        Set<UUID> changedVariantIds,
        List<WaitingStockItemResponse> missingItems
) {
    public static ReservationOutcome of(ReservationResult result) {
        return new ReservationOutcome(result, Set.of(), List.of());
    }
}
