package fu.osms.sync.service.impl;

import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncJobProgressTracker {

    private final SyncLogRepository syncLogRepository;
    private final ThreadLocal<UUID> currentJobId = new ThreadLocal<>();

    public void bind(UUID jobId) {
        currentJobId.set(jobId);
    }

    public void clear() {
        currentJobId.remove();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordVariantBatch(int processedVariants) {
        UUID jobId = currentJobId.get();
        if (jobId == null || processedVariants <= 0) {
            return;
        }
        SyncLog job = syncLogRepository.findById(jobId).orElse(null);
        if (job == null || job.getCompletedAt() != null) {
            return;
        }
        job.setSuccessCount(safe(job.getSuccessCount()) + processedVariants);
        int estimatedTotal = safe(job.getTotalItems());
        if (job.getSuccessCount() > estimatedTotal) {
            job.setTotalItems(job.getSuccessCount());
        }
        syncLogRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID jobId, int variants) {
        SyncLog job = syncLogRepository.findById(jobId).orElseThrow();
        job.setStatus(SyncStatus.SYNCED);
        job.setTotalItems(Math.max(variants, 0));
        job.setSuccessCount(Math.max(variants, 0));
        job.setFailCount(0);
        job.setErrorSummary(null);
        job.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(UUID jobId, String errorMessage) {
        SyncLog job = syncLogRepository.findById(jobId).orElseThrow();
        job.setStatus(SyncStatus.FAILED);
        job.setFailCount(safe(job.getFailCount()) + 1);
        job.setErrorSummary(errorMessage);
        job.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(job);
    }

    private int safe(Integer value) {
        return value == null ? 0 : value;
    }
}
