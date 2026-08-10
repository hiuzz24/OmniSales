package fu.osms.sync.order.pull.impl;

import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.order.pull.OrderPullJobContext;
import fu.osms.sync.order.pull.OrderPullJobState;
import fu.osms.sync.order.pull.OrderPullJobStore;
import fu.osms.sync.order.pull.entity.OrderPullJob;
import fu.osms.sync.order.pull.repository.OrderPullJobRepository;
import fu.osms.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPullJobStoreImpl implements OrderPullJobStore {

    private static final int MAX_ERROR_LENGTH = 4000;

    private final OrderPullJobRepository jobRepository;
    private final SyncLogRepository syncLogRepository;

    @Override
    @Transactional
    public UUID create(SyncLog syncLog, OffsetDateTime from, OffsetDateTime to) {
        OrderPullJob job = jobRepository.save(OrderPullJob.builder()
                .syncLog(syncLog)
                .fromTime(from)
                .toTime(to)
                .state(OrderPullJobState.PENDING)
                .attemptCount(0)
                .build());
        return job.getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean prepareDispatch(UUID jobId) {
        OrderPullJob job = lock(jobId);
        if (job.getState() != OrderPullJobState.PENDING) {
            return false;
        }
        markPublished(job, OffsetDateTime.now());
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<OrderPullJobContext> claim(UUID jobId, int maxAttempts) {
        OrderPullJob job = lock(jobId);
        if (isTerminal(job.getState()) || job.getState() == OrderPullJobState.PROCESSING) {
            return Optional.empty();
        }
        int attempts = value(job.getAttemptCount());
        if (attempts >= maxAttempts) {
            failPermanently(job, "Order pull exceeded the maximum number of attempts");
            return Optional.empty();
        }

        OffsetDateTime now = OffsetDateTime.now();
        job.setState(OrderPullJobState.PROCESSING);
        job.setAttemptCount(attempts + 1);
        if (job.getStartedAt() == null) {
            job.setStartedAt(now);
        }
        job.setLastHeartbeatAt(now);
        job.setLastError(null);

        SyncLog syncLog = job.getSyncLog();
        return Optional.of(new OrderPullJobContext(
                job.getId(),
                syncLog.getId(),
                syncLog.getChannel(),
                job.getFromTime(),
                job.getToTime(),
                job.getAttemptCount()));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID jobId, SyncStatus status, int total, int success, int failed, String error) {
        OrderPullJob job = lock(jobId);
        if (isTerminal(job.getState())) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        job.setState(OrderPullJobState.COMPLETED);
        job.setCompletedAt(now);
        job.setLastHeartbeatAt(now);
        job.setLastError(shorten(error));
        completeSyncLog(job.getSyncLog(), status, total, success, failed, error, now);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failAttempt(UUID jobId, String error, int maxAttempts) {
        OrderPullJob job = lock(jobId);
        if (isTerminal(job.getState())) {
            return false;
        }
        job.setLastError(shorten(error));
        job.setLastHeartbeatAt(OffsetDateTime.now());
        if (value(job.getAttemptCount()) >= maxAttempts) {
            failPermanently(job, error);
            return false;
        }
        job.setState(OrderPullJobState.PENDING);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<UUID> claimRecoverable(
            OffsetDateTime publishedCutoff,
            OffsetDateTime processingCutoff,
            int maxAttempts,
            int batchSize) {
        List<UUID> ids = jobRepository.claimRecoverableIds(
                publishedCutoff, processingCutoff, batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<OrderPullJob> jobs = jobRepository.findAllById(ids);
        List<UUID> dispatchableIds = new ArrayList<>();
        for (OrderPullJob job : jobs) {
            if (value(job.getAttemptCount()) >= maxAttempts) {
                failPermanently(job, "Order pull exceeded the maximum number of attempts");
                continue;
            }
            markPublished(job, now);
            dispatchableIds.add(job.getId());
        }
        jobRepository.saveAll(jobs);
        return dispatchableIds;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForSyncLog(UUID syncLogId) {
        return jobRepository.existsBySyncLog_Id(syncLogId);
    }

    private OrderPullJob lock(UUID jobId) {
        return jobRepository.findForUpdateWithContext(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Order pull job not found: " + jobId));
    }

    private void markPublished(OrderPullJob job, OffsetDateTime now) {
        job.setState(OrderPullJobState.PUBLISHED);
        job.setPublishedAt(now);
        job.setLastHeartbeatAt(now);
    }

    private void failPermanently(OrderPullJob job, String error) {
        OffsetDateTime now = OffsetDateTime.now();
        job.setState(OrderPullJobState.FAILED);
        job.setCompletedAt(now);
        job.setLastHeartbeatAt(now);
        job.setLastError(shorten(error));
        completeSyncLog(job.getSyncLog(), SyncStatus.FAILED, 0, 0, 1, error, now);
    }

    private void completeSyncLog(
            SyncLog log,
            SyncStatus status,
            int total,
            int success,
            int failed,
            String error,
            OffsetDateTime completedAt) {
        log.setStatus(status);
        log.setTotalItems(total);
        log.setSuccessCount(success);
        log.setFailCount(failed);
        log.setErrorSummary(shorten(error));
        log.setCompletedAt(completedAt);
        syncLogRepository.save(log);
    }

    private boolean isTerminal(OrderPullJobState state) {
        return state == OrderPullJobState.COMPLETED || state == OrderPullJobState.FAILED;
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String shorten(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
