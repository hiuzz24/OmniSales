package fu.osms.sync.service;

import java.util.UUID;

public interface InventoryReconciliationSyncLogService {

    UUID start(UUID channelId, int totalItems);

    void markSynced(UUID id, int successCount);

    void markFailed(UUID id, int failCount, String error);
}
