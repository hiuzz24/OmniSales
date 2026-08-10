package fu.osms.sync.order.pull.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.mapper.SyncLogMapper;
import fu.osms.sync.order.pull.OrderPullJobStore;
import fu.osms.sync.order.pull.OrderPullRequestedEvent;
import fu.osms.sync.order.pull.dto.OrderPullRequest;
import fu.osms.sync.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPullServiceImpl Tests")
class OrderPullServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelConnectionValidator connectionValidator;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private SyncLogMapper syncLogMapper;
    @Mock private OrderPullJobStore orderPullJobStore;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderPullServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderPullServiceImpl(channelRepository, connectionValidator,
                syncLogRepository, syncLogMapper, orderPullJobStore, eventPublisher);
    }

    private Channel channel(UUID id, PlatformType platform, Map<String, Object> metadata) {
        return Channel.builder()
                .id(id)
                .platform(platform)
                .metadata(metadata == null ? null : new HashMap<>(metadata))
                .build();
    }

    private SyncLog syncLog(UUID id, SyncStatus status) {
        return SyncLog.builder().id(id).jobType("ORDER_PULL").status(status).build();
    }

    @Test
    @DisplayName("start: throws IllegalArgumentException when from > to")
    void start_invalidRange() {
        OrderPullRequest request = new OrderPullRequest(
                List.of(UUID.randomUUID()),
                OffsetDateTime.now(),
                OffsetDateTime.now().minusHours(1));

        assertThatThrownBy(() -> service.start(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start: throws IllegalArgumentException when range exceeds 7 days")
    void start_rangeTooLarge() {
        OrderPullRequest request = new OrderPullRequest(
                List.of(UUID.randomUUID()),
                OffsetDateTime.now().minusDays(10),
                OffsetDateTime.now());

        assertThatThrownBy(() -> service.start(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start: throws when channel platform is not supported")
    void start_unsupportedPlatform() {
        UUID channelId = UUID.randomUUID();
        OrderPullRequest request = new OrderPullRequest(
                List.of(channelId), null, null);
        when(channelRepository.findForUpdateById(channelId))
                .thenReturn(Optional.of(channel(channelId, PlatformType.MANUAL, null)));

        assertThatThrownBy(() -> service.start(request)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("start: throws when Shopify channel is missing shopDomain")
    void start_shopifyMissingDomain() {
        UUID channelId = UUID.randomUUID();
        OrderPullRequest request = new OrderPullRequest(
                List.of(channelId), null, null);
        when(channelRepository.findForUpdateById(channelId))
                .thenReturn(Optional.of(channel(channelId, PlatformType.SHOPIFY, null)));

        assertThatThrownBy(() -> service.start(request)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("start: returns existing PENDING job when one is already active for the channel")
    void start_existingActiveJob() {
        UUID channelId = UUID.randomUUID();
        OrderPullRequest request = new OrderPullRequest(
                List.of(channelId), null, null);
        SyncLog existing = syncLog(UUID.randomUUID(), SyncStatus.PENDING);
        when(channelRepository.findForUpdateById(channelId))
                .thenReturn(Optional.of(channel(channelId, PlatformType.LAZADA, null)));
        when(syncLogRepository.findByJobTypeAndChannelIdAndStatus("ORDER_PULL", channelId, SyncStatus.PENDING))
                .thenReturn(List.of(existing));
        when(syncLogMapper.toResponse(existing)).thenReturn(new fu.osms.sync.dto.SyncLogResponse());

        service.start(request);

        verify(syncLogRepository, times(0)).save(any(SyncLog.class));
        verify(eventPublisher, times(0)).publishEvent(any());
    }

    @Test
    @DisplayName("start: happy path creates a new sync log and publishes OrderPullRequestedEvent")
    void start_happyPath() {
        UUID channelId = UUID.randomUUID();
        OrderPullRequest request = new OrderPullRequest(
                List.of(channelId), null, null);
        SyncLog newLog = syncLog(UUID.randomUUID(), SyncStatus.PENDING);
        when(channelRepository.findForUpdateById(channelId))
                .thenReturn(Optional.of(channel(channelId, PlatformType.LAZADA, null)));
        when(syncLogRepository.findByJobTypeAndChannelIdAndStatus("ORDER_PULL", channelId, SyncStatus.PENDING))
                .thenReturn(List.of());
        when(syncLogRepository.save(any(SyncLog.class))).thenReturn(newLog);

        service.start(request);

        verify(eventPublisher).publishEvent(any(OrderPullRequestedEvent.class));
        ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
        verify(syncLogRepository).save(captor.capture());
        assertThat(captor.getValue().getJobType()).isEqualTo("ORDER_PULL");
        assertThat(captor.getValue().getIdempotencyKey()).startsWith("ORDER_PULL:" + channelId + ":");
    }

    @Test
    @DisplayName("start: rethrows channel-connection validator exceptions")
    void start_connectionFails() {
        UUID channelId = UUID.randomUUID();
        OrderPullRequest request = new OrderPullRequest(
                List.of(channelId), null, null);
        when(channelRepository.findForUpdateById(channelId))
                .thenReturn(Optional.of(channel(channelId, PlatformType.LAZADA, null)));
        doThrow(new RuntimeException("not connected"))
                .when(connectionValidator).validateConnected(any());

        assertThatThrownBy(() -> service.start(request)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("get: throws IllegalArgumentException when job not found")
    void get_notFound() {
        UUID id = UUID.randomUUID();
        when(syncLogRepository.findWithChannelById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("get: throws when sync log is not an ORDER_PULL job")
    void get_wrongJobType() {
        UUID id = UUID.randomUUID();
        SyncLog log = syncLog(id, SyncStatus.PENDING);
        log.setJobType("OTHER");
        when(syncLogRepository.findWithChannelById(id)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("active: returns mapped list of active jobs")
    void active() {
        SyncLog log = syncLog(UUID.randomUUID(), SyncStatus.PENDING);
        when(syncLogRepository.findActiveJobs("ORDER_PULL", SyncStatus.PENDING))
                .thenReturn(List.of(log));
        when(syncLogMapper.toResponse(log)).thenReturn(new fu.osms.sync.dto.SyncLogResponse());

        List<fu.osms.sync.dto.SyncLogResponse> result = service.active();

        assertThat(result).hasSize(1);
    }
}