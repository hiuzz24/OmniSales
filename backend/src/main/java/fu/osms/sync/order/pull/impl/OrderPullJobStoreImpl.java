package fu.osms.sync.order.pull.impl;

import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.order.pull.OrderPullJobContext;
import fu.osms.sync.order.pull.OrderPullJobStore;
import fu.osms.sync.repository.SyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderPullJobStoreImpl implements OrderPullJobStore {
    private final SyncLogRepository repository;

    @Override
    @Transactional(readOnly = true)
    public OrderPullJobContext load(UUID id) {
        SyncLog log = repository.findWithChannelById(id)
                .orElseThrow(() -> new IllegalArgumentException("Order pull job not found: " + id));
        log.getChannel().getMetadata();
        return new OrderPullJobContext(id, log.getChannel());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID id, SyncStatus status, int total, int success, int failed, String error) {
        SyncLog log = repository.findById(id).orElseThrow();
        log.setStatus(status);
        log.setTotalItems(total);
        log.setSuccessCount(success);
        log.setFailCount(failed);
        log.setErrorSummary(error);
        log.setCompletedAt(OffsetDateTime.now());
        repository.save(log);
    }
}
