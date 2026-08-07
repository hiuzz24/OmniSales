package fu.osms.sync.inventory;

import java.util.List;
import java.util.UUID;

public interface InventoryReconciliationTaskService {

    List<InventoryReconciliationTask> claimDueTasks();

    void markSynced(UUID mappingId, String cycleId);

    void scheduleVerification(UUID mappingId, String cycleId);

    void scheduleRetry(UUID mappingId, String cycleId, int nextAttempt, String error);

    void markFailed(UUID mappingId, String cycleId, String error);
}
