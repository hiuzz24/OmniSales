package fu.osms.sync.service.impl;

import fu.osms.sync.service.InventoryAutoPushSyncLogService;
import fu.osms.sync.service.SyncLogLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryAutoPushSyncLogServiceImpl implements InventoryAutoPushSyncLogService {

    public static final String JOB_TYPE = "MARKETPLACE_INVENTORY_AUTO_PUSH";

    private final SyncLogLifecycleService lifecycle;

    @Override
    public UUID start(UUID channelId, int totalItems) {
        return lifecycle.start(channelId, JOB_TYPE, totalItems);
    }

    @Override
    public void markSynced(UUID syncLogId, int successCount) {
        lifecycle.markSynced(syncLogId, successCount);
    }

    @Override
    public void markFailed(UUID syncLogId, int failCount, String errorSummary) {
        lifecycle.markFailed(syncLogId, failCount, errorSummary);
    }
}
