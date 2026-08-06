package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.sync.dto.MarketplaceSyncJobResponse;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceSyncJobServiceImpl Tests")
class MarketplaceSyncJobServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private MarketplaceSyncJobWorker worker;

    private MarketplaceSyncJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceSyncJobServiceImpl(channelRepository, syncLogRepository, worker);
    }

    private Channel channel(UUID id, boolean syncEnabled, Map<String, Object> metadata) {
        return Channel.builder()
                .id(id)
                .platform(PlatformType.LAZADA)
                .displayName("Test Channel")
                .syncEnabled(syncEnabled)
                .metadata(metadata == null ? null : new HashMap<>(metadata))
                .build();
    }

    @Test
    @DisplayName("enqueueRemoteSync: throws AppException when channel not found or deleted")
    void enqueue_channelNotFound() {
        UUID id = UUID.randomUUID();
        when(channelRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enqueueRemoteSync(id))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("enqueueRemoteSync: throws AppException when sync is disabled")
    void enqueue_syncDisabled() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, false, null);
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));

        assertThatThrownBy(() -> service.enqueueRemoteSync(id))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("enqueueRemoteSync: returns existing RUNNING job when one is already PENDING for this channel")
    void enqueue_existingPendingJob() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, true, Map.of("skuVariantCount", 10));
        UUID jobId = UUID.randomUUID();
        SyncLog existing = SyncLog.builder()
                .id(jobId)
                .channel(ch)
                .jobType("LAZADA_REMOTE_IMPORT_JOB")
                .status(SyncStatus.PENDING)
                .totalItems(0)
                .successCount(0)
                .failCount(0)
                .startedAt(OffsetDateTime.now())
                .build();
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(syncLogRepository.findFirstByChannelIdAndStatusAndCompletedAtIsNullAndJobTypeEndingWithOrderByStartedAtDesc(
                eq(id), eq(SyncStatus.PENDING), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(existing));

        MarketplaceSyncJobResponse resp = service.enqueueRemoteSync(id);

        assertThat(resp.getStatus()).isEqualTo("RUNNING");
        assertThat(resp.getJobId()).isEqualTo(jobId);
        verify(syncLogRepository, never()).save(any(SyncLog.class));
        verify(worker, never()).executeRemoteSync(any(), any());
    }

    @Test
    @DisplayName("enqueueRemoteSync: creates a new job, dispatches to worker, returns QUEUED")
    void enqueue_newJob() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, true, Map.of("skuVariantCount", 42));
        UUID newJobId = UUID.randomUUID();
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(syncLogRepository.findFirstByChannelIdAndStatusAndCompletedAtIsNullAndJobTypeEndingWithOrderByStartedAtDesc(
                eq(id), eq(SyncStatus.PENDING), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> {
            SyncLog log = inv.getArgument(0);
            log.setId(newJobId);
            return log;
        });

        MarketplaceSyncJobResponse resp = service.enqueueRemoteSync(id);

        assertThat(resp.getStatus()).isEqualTo("QUEUED");
        assertThat(resp.getJobId()).isEqualTo(newJobId);
        assertThat(resp.getTotalItems()).isEqualTo(42);
        verify(worker).executeRemoteSync(eq(newJobId), eq(id));
    }

    @Test
    @DisplayName("enqueueRemoteSync: marks the job FAILED when worker rejects the task")
    void enqueue_workerRejects() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, true, null);
        UUID newJobId = UUID.randomUUID();
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(syncLogRepository.findFirstByChannelIdAndStatusAndCompletedAtIsNullAndJobTypeEndingWithOrderByStartedAtDesc(
                eq(id), eq(SyncStatus.PENDING), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> {
            SyncLog log = inv.getArgument(0);
            log.setId(newJobId);
            return log;
        });
        doThrow(new TaskRejectedException("queue full")).when(worker).executeRemoteSync(eq(newJobId), eq(id));

        assertThatThrownBy(() -> service.enqueueRemoteSync(id))
                .isInstanceOf(AppException.class);

        ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
        verify(syncLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        SyncLog finalSave = captor.getAllValues().get(1);
        assertThat(finalSave.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(finalSave.getFailCount()).isEqualTo(1);
        assertThat(finalSave.getErrorSummary()).contains("đầy");
        assertThat(finalSave.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("enqueueRemoteSync: defaults totalItems to 0 when metadata missing or unparseable")
    void enqueue_noEstimate() {
        UUID id = UUID.randomUUID();
        Channel ch = channel(id, true, Map.of("otherKey", "value"));
        when(channelRepository.findById(id)).thenReturn(Optional.of(ch));
        when(syncLogRepository.findFirstByChannelIdAndStatusAndCompletedAtIsNullAndJobTypeEndingWithOrderByStartedAtDesc(
                eq(id), eq(SyncStatus.PENDING), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(inv -> inv.getArgument(0));

        MarketplaceSyncJobResponse resp = service.enqueueRemoteSync(id);

        assertThat(resp.getTotalItems()).isZero();
    }

    @Test
    @DisplayName("getJob: throws AppException when job not found")
    void getJob_notFound() {
        UUID jobId = UUID.randomUUID();
        when(syncLogRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getJob(jobId)).isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("getJob: maps SYNCED status to 'SYNCED' with success message")
    void getJob_synced() {
        UUID jobId = UUID.randomUUID();
        SyncLog log = SyncLog.builder()
                .id(jobId)
                .jobType("LAZADA_REMOTE_IMPORT_JOB")
                .status(SyncStatus.SYNCED)
                .channel(channel(UUID.randomUUID(), true, null))
                .totalItems(10)
                .successCount(10)
                .failCount(0)
                .startedAt(OffsetDateTime.now().minusSeconds(60))
                .completedAt(OffsetDateTime.now())
                .build();
        when(syncLogRepository.findById(jobId)).thenReturn(Optional.of(log));

        MarketplaceSyncJobResponse resp = service.getJob(jobId);

        assertThat(resp.getStatus()).isEqualTo("SYNCED");
        assertThat(resp.getMessage()).contains("hoàn tất");
        assertThat(resp.getProgressPercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("getJob: maps FAILED status with the errorSummary as message")
    void getJob_failed() {
        UUID jobId = UUID.randomUUID();
        SyncLog log = SyncLog.builder()
                .id(jobId)
                .jobType("LAZADA_REMOTE_IMPORT_JOB")
                .status(SyncStatus.FAILED)
                .errorSummary("channel offline")
                .channel(channel(UUID.randomUUID(), true, null))
                .build();
        when(syncLogRepository.findById(jobId)).thenReturn(Optional.of(log));

        MarketplaceSyncJobResponse resp = service.getJob(jobId);

        assertThat(resp.getStatus()).isEqualTo("FAILED");
        assertThat(resp.getMessage()).isEqualTo("channel offline");
    }

    @Test
    @DisplayName("getJob: maps PENDING to 'RUNNING' with batch processing message")
    void getJob_pending() {
        UUID jobId = UUID.randomUUID();
        SyncLog log = SyncLog.builder()
                .id(jobId)
                .jobType("LAZADA_REMOTE_IMPORT_JOB")
                .status(SyncStatus.PENDING)
                .channel(channel(UUID.randomUUID(), true, null))
                .build();
        when(syncLogRepository.findById(jobId)).thenReturn(Optional.of(log));

        MarketplaceSyncJobResponse resp = service.getJob(jobId);

        assertThat(resp.getStatus()).isEqualTo("RUNNING");
        assertThat(resp.getMessage()).contains("batch");
    }

    @Test
    @DisplayName("getJob: percent calculation caps at 100 when processed > total")
    void getJob_percentCap() {
        UUID jobId = UUID.randomUUID();
        SyncLog log = SyncLog.builder()
                .id(jobId)
                .jobType("LAZADA_REMOTE_IMPORT_JOB")
                .status(SyncStatus.SYNCED)
                .channel(channel(UUID.randomUUID(), true, null))
                .totalItems(5)
                .successCount(5)
                .failCount(5)
                .build();
        when(syncLogRepository.findById(jobId)).thenReturn(Optional.of(log));

        MarketplaceSyncJobResponse resp = service.getJob(jobId);

        assertThat(resp.getProgressPercent()).isEqualTo(100);
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
