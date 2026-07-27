package fu.osms.sync.service;

import java.util.UUID;

public interface InventoryAutoPushSyncLogService {

    UUID start(UUID channelId, int totalItems);

    void markSynced(UUID syncLogId, int successCount);

    void markFailed(UUID syncLogId, int failCount, String errorSummary);
}
