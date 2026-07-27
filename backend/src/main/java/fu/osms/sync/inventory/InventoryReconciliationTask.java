package fu.osms.sync.inventory;

import fu.osms.common.enums.PlatformType;

import java.util.UUID;

public record InventoryReconciliationTask(
        UUID mappingId,
        UUID channelId,
        PlatformType platform,
        String cycleId,
        String step,
        int apiAttemptCount,
        int correctivePushCount,
        String externalStockLocation
) {
}
