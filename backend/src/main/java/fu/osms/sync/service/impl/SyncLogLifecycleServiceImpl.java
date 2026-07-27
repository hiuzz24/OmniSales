package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.SyncLogLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SyncLogLifecycleServiceImpl implements SyncLogLifecycleService {

    private final ChannelRepository channelRepository;
    private final SyncLogRepository syncLogRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(UUID channelId, String jobType, int totalItems) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new AppException(ErrorCode.CHANNEL_NOT_FOUND));
        return syncLogRepository.save(SyncLog.builder()
                .channel(channel)
                .jobType(jobType)
                .status(SyncStatus.PENDING)
                .totalItems(Math.max(totalItems, 0))
                .successCount(0)
                .failCount(0)
                .startedAt(OffsetDateTime.now())
                .build()).getId();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSynced(UUID id, int successCount) {
        SyncLog log = require(id);
        log.setStatus(SyncStatus.SYNCED);
        log.setSuccessCount(Math.max(successCount, 0));
        log.setFailCount(0);
        log.setErrorSummary(null);
        log.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(log);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, int failCount, String error) {
        SyncLog log = require(id);
        log.setStatus(SyncStatus.FAILED);
        log.setSuccessCount(0);
        log.setFailCount(Math.max(failCount, 1));
        log.setErrorSummary(error);
        log.setCompletedAt(OffsetDateTime.now());
        syncLogRepository.save(log);
    }

    private SyncLog require(UUID id) {
        return syncLogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sync log not found: " + id));
    }
}
