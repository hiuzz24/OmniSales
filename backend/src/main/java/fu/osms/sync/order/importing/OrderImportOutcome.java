package fu.osms.sync.order.importing;

import java.util.UUID;

public record OrderImportOutcome(
        UUID orderId,
        OrderImportResult result,
        boolean created,
        boolean paymentBecamePaid,
        boolean becameCancelled,
        boolean platformMetadataUpdated
) {
}
