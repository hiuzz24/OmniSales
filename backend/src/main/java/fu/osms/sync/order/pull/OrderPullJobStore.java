package fu.osms.sync.order.pull;

import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.entity.SyncLog;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPullJobStore {
    UUID create(SyncLog syncLog, OffsetDateTime from, OffsetDateTime to);

    boolean prepareDispatch(UUID jobId);

    Optional<OrderPullJobContext> claim(UUID jobId, int maxAttempts);

    void complete(UUID jobId, SyncStatus status, int total, int success, int failed, String error);

    boolean failAttempt(UUID jobId, String error, int maxAttempts);

    List<UUID> claimRecoverable(
            OffsetDateTime publishedCutoff,
            OffsetDateTime processingCutoff,
            int maxAttempts,
            int batchSize);

    boolean existsForSyncLog(UUID syncLogId);
}
