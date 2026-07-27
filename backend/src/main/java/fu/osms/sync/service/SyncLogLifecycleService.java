package fu.osms.sync.service;

import java.util.UUID;

public interface SyncLogLifecycleService {

    UUID start(UUID channelId, String jobType, int totalItems);

    void markSynced(UUID id, int successCount);

    void markFailed(UUID id, int failCount, String error);
}
