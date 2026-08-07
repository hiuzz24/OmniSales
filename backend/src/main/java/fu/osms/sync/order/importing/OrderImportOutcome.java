package fu.osms.sync.order.importing;

import fu.osms.order.enums.OrderStatus;

import java.util.UUID;

public record OrderImportOutcome(
        UUID orderId,
        OrderImportResult result,
        boolean created,
        boolean paymentBecamePaid,
        boolean becameCancelled,
        boolean platformMetadataUpdated,
        OrderStatus previousStatus,
        OrderStatus currentStatus
) {
    public boolean statusChanged() {
        return previousStatus != null && currentStatus != null && previousStatus != currentStatus;
    }
}
