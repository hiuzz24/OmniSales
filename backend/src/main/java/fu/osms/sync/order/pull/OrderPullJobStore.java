package fu.osms.sync.order.pull;

import fu.osms.common.enums.SyncStatus;

import java.util.UUID;

public interface OrderPullJobStore {
    OrderPullJobContext load(UUID id);
    void complete(UUID id, SyncStatus status, int total, int success, int failed, String error);
}
