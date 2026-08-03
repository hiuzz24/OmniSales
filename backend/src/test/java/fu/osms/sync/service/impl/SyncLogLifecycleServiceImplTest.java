package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncLogLifecycleServiceImpl Tests")
class SyncLogLifecycleServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private SyncLogRepository syncLogRepository;

    private SyncLogLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SyncLogLifecycleServiceImpl(channelRepository, syncLogRepository);
    }

    private Channel channel(UUID id) {
        return Channel.builder().id(id).build();
    }

    private SyncLog savedLog(UUID id) {
        return SyncLog.builder().id(id).jobType("JOB").status(SyncStatus.PENDING).build();
    }

    @Test
    @DisplayName("start: persists a PENDING log with channel, jobType, totalItems >= 0, startedAt set")
    void start() {
        UUID channelId = UUID.randomUUID();
        UUID newId = UUID.randomUUID();
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel(channelId)));
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> {
            SyncLog log = inv.getArgument(0);
            log.setId(newId);
            return log;
        });

        UUID id = service.start(channelId, "PRODUCT_SYNC", -5);

        ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        SyncLog saved = captor.getValue();
        assertThat(saved.getChannel().getId()).isEqualTo(channelId);
        assertThat(saved.getJobType()).isEqualTo("PRODUCT_SYNC");
        assertThat(saved.getStatus()).isEqualTo(SyncStatus.PENDING);
        assertThat(saved.getTotalItems()).isZero(); // -5 clamped to 0
        assertThat(saved.getSuccessCount()).isZero();
        assertThat(saved.getFailCount()).isZero();
        assertThat(saved.getStartedAt()).isNotNull();
        assertThat(id).isEqualTo(newId);
    }

    @Test
    @DisplayName("start: throws AppException(CHANNEL_NOT_FOUND) when channel doesn't exist")
    void start_channelNotFound() {
        UUID channelId = UUID.randomUUID();
        when(channelRepository.findById(channelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(channelId, "X", 0))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("markSynced: sets status to SYNCED, successCount clamped to >=0, failCount=0, completedAt set")
    void markSynced() {
        UUID id = UUID.randomUUID();
        SyncLog log = savedLog(id);
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(log));

        service.markSynced(id, -10);

        assertThat(log.getStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(log.getSuccessCount()).isZero();
        assertThat(log.getFailCount()).isZero();
        assertThat(log.getErrorSummary()).isNull();
        assertThat(log.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed: sets status to FAILED, failCount clamped to >=1, completedAt set, error stored")
    void markFailed() {
        UUID id = UUID.randomUUID();
        SyncLog log = savedLog(id);
        when(syncLogRepository.findById(id)).thenReturn(Optional.of(log));

        service.markFailed(id, 0, "boom");

        assertThat(log.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(log.getSuccessCount()).isZero();
        assertThat(log.getFailCount()).isEqualTo(1); // 0 clamped to 1
        assertThat(log.getErrorSummary()).isEqualTo("boom");
        assertThat(log.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("markFailed: throws IllegalArgumentException when log not found")
    void markFailed_logNotFound() {
        UUID id = UUID.randomUUID();
        when(syncLogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markFailed(id, 5, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("markSynced: throws IllegalArgumentException when log not found")
    void markSynced_logNotFound() {
        UUID id = UUID.randomUUID();
        when(syncLogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markSynced(id, 5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
