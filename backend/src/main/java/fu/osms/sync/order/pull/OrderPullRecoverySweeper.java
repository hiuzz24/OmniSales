package fu.osms.sync.order.pull;

import fu.osms.sync.order.pull.impl.OrderPullDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPullRecoverySweeper {

    private final OrderPullRecoveryProperties properties;
    private final OrderPullJobStore jobStore;
    private final OrderPullDispatchService dispatchService;

    /** Phát lại job bền vững bị treo do ứng dụng lỗi hoặc broker gián đoạn. */
    @Scheduled(fixedDelayString = "${app.order-pull-recovery.fixed-delay-ms:30000}")
    public void sweep() {
        if (!properties.isEnabled()) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> jobIds = jobStore.claimRecoverable(
                now.minusNanos(properties.getPublishedStaleMs() * 1_000_000),
                now.minusNanos(properties.getProcessingStaleMs() * 1_000_000),
                properties.getMaxAttempts(),
                properties.getBatchSize());
        if (jobIds.isEmpty()) {
            return;
        }
        log.info("[OrderPullRecovery] Re-enqueueing {} durable order pull jobs", jobIds.size());
        jobIds.forEach(jobId -> {
            try {
                dispatchService.publishPrepared(jobId);
            } catch (Exception exception) {
                log.warn("[OrderPullRecovery] Failed to publish jobId={}", jobId, exception);
            }
        });
    }
}
