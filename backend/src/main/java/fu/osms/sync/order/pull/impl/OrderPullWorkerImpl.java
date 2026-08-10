package fu.osms.sync.order.pull.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.order.pull.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderPullWorkerImpl implements OrderPullWorker {

    private final OrderPullJobStore jobStore;
    private final OrderPullRecoveryProperties recoveryProperties;
    private final List<PlatformOrderImporter> importers;

    @Override
    public void process(UUID orderPullJobId) {
        OrderPullJobContext job = jobStore.claim(orderPullJobId, recoveryProperties.getMaxAttempts())
                .orElse(null);
        if (job == null) {
            log.debug("[OrderPullWorker] Ignoring duplicate or terminal jobId={}", orderPullJobId);
            return;
        }

        try {
            PlatformOrderImporter importer = importerMap().get(job.channel().getPlatform());
            if (importer == null) {
                throw new IllegalStateException(
                        "Order pull is not supported for " + job.channel().getPlatform());
            }
            OrderPullBatchResult result = importer.pull(job.channel(), job.from(), job.to());
            jobStore.complete(
                    orderPullJobId,
                    result.failed() == 0 ? SyncStatus.SYNCED : SyncStatus.FAILED,
                    result.total(),
                    result.success(),
                    result.failed(),
                    summarize(result.errors()));
        } catch (Exception exception) {
            String error = "[PAGE] " + message(exception);
            boolean retryable = jobStore.failAttempt(
                    orderPullJobId, error, recoveryProperties.getMaxAttempts());
            log.error("[OrderPullWorker] Pull failed jobId={} attempt={}",
                    orderPullJobId, job.attemptCount(), exception);
            if (retryable) {
                throw new IllegalStateException(error, exception);
            }
        }
    }

    private Map<PlatformType, PlatformOrderImporter> importerMap() {
        Map<PlatformType, PlatformOrderImporter> result = new EnumMap<>(PlatformType.class);
        importers.forEach(importer -> result.put(importer.platform(), importer));
        return result;
    }

    private String summarize(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        String value = String.join("\n", errors.stream().limit(20).toList());
        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }

    private String message(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
