package fu.osms.sync.order.pull.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.order.pull.OrderPullBatchResult;
import fu.osms.sync.order.pull.OrderPullJobContext;
import fu.osms.sync.order.pull.OrderPullJobStore;
import fu.osms.sync.order.pull.OrderPullWorker;
import fu.osms.sync.order.pull.PlatformOrderImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPullWorkerImpl implements OrderPullWorker {
    private final OrderPullJobStore jobStore;
    private final List<PlatformOrderImporter> importers;

    @Override
    @Async("orderPullExecutor")
    public void processAsync(UUID syncLogId, OffsetDateTime from, OffsetDateTime to) {
        try {
            OrderPullJobContext job = jobStore.load(syncLogId);
            PlatformOrderImporter importer = importerMap().get(job.channel().getPlatform());
            if (importer == null) throw new IllegalStateException("Order pull is not supported for " + job.channel().getPlatform());
            OrderPullBatchResult result = importer.pull(job.channel(), from, to);
            jobStore.complete(syncLogId, result.failed() == 0 ? SyncStatus.SYNCED : SyncStatus.FAILED,
                    result.total(), result.success(), result.failed(), summarize(result.errors()));
        } catch (Exception e) {
            jobStore.complete(syncLogId, SyncStatus.FAILED, 0, 0, 1, "[PAGE] " + message(e));
        }
    }

    @Override
    public void markQueueRejected(UUID syncLogId) {
        jobStore.complete(syncLogId, SyncStatus.FAILED, 0, 0, 1, "[PAGE] Order pull queue is full");
    }

    private Map<PlatformType, PlatformOrderImporter> importerMap() {
        Map<PlatformType, PlatformOrderImporter> result = new EnumMap<>(PlatformType.class);
        importers.forEach(importer -> result.put(importer.platform(), importer));
        return result;
    }
    private String summarize(List<String> errors) {
        if (errors == null || errors.isEmpty()) return null;
        String value = String.join("\n", errors.stream().limit(20).toList());
        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }
    private String message(Exception e) { return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
}
