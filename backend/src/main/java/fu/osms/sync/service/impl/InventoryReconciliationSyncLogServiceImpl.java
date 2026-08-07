package fu.osms.sync.service.impl;

import fu.osms.sync.service.InventoryReconciliationSyncLogService;
import fu.osms.sync.service.SyncLogLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryReconciliationSyncLogServiceImpl
        implements InventoryReconciliationSyncLogService {

    public static final String JOB_TYPE = "MARKETPLACE_INVENTORY_RECONCILE";

    private final SyncLogLifecycleService lifecycle;

    @Override
    public UUID start(UUID channelId, int totalItems) {
        return lifecycle.start(channelId, JOB_TYPE, totalItems);
    }

    @Override
    public void markSynced(UUID id, int successCount) {
        lifecycle.markSynced(id, successCount);
    }

    @Override
    public void markFailed(UUID id, int failCount, String error) {
        lifecycle.markFailed(id, failCount, error);
    }
}
