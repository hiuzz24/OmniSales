package fu.osms.sync.inventory;

import fu.osms.common.enums.PlatformType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record InventoryObservation(
        UUID mappingId,
        PlatformType platform,
        int remoteAvailable,
        OffsetDateTime observedAt,
        String externalStockLocation,
        UUID webhookEventId
) {
}
