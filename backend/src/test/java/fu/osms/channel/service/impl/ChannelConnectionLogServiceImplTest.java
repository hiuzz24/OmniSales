package fu.osms.channel.service.impl;

import fu.osms.channel.dto.response.ChannelConnectionLogResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelConnectionLog;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.enums.ChannelConnectionLogStatus;
import fu.osms.channel.mapper.ChannelConnectionLogMapper;
import fu.osms.channel.repository.ChannelConnectionLogRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelConnectionLogServiceImpl - Unit Tests")
class ChannelConnectionLogServiceImplTest {

    @Mock
    private ChannelConnectionLogRepository repository;

    @Mock
    private ChannelConnectionLogMapper mapper;

    @InjectMocks
    private ChannelConnectionLogServiceImpl service;

    private ChannelConnectionLog sample;
    private Channel sampleChannel;

    @BeforeEach
    void setUp() {
        sampleChannel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .displayName("my-shop")
                .build();

        sample = ChannelConnectionLog.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .channel(sampleChannel)
                .channelName("my-shop")
                .action(ChannelConnectionAction.CONNECT)
                .status(ChannelConnectionLogStatus.SUCCESS)
                .message("Connected")
                .metadata(Map.of("shopDomain", "my-shop"))
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("search - returns paginated logs mapped to response DTO")
    void search_returnsMappedLogs() {
        ChannelConnectionLogResponse dto = ChannelConnectionLogResponse.builder()
                .id(sample.getId())
                .platform(PlatformType.SHOPIFY)
                .channelName("my-shop")
                .action(ChannelConnectionAction.CONNECT)
                .status(ChannelConnectionLogStatus.SUCCESS)
                .build();
        Page<ChannelConnectionLog> page = new PageImpl<>(List.of(sample));
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(mapper.toResponse(sample)).thenReturn(dto);

        PageResponse<ChannelConnectionLogResponse> result = service.search(
                PlatformType.SHOPIFY,
                ChannelConnectionLogStatus.SUCCESS,
                ChannelConnectionAction.CONNECT,
                sampleChannel.getId(),
                0, 20
        );

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getPlatform()).isEqualTo(PlatformType.SHOPIFY);
        assertThat(result.getTotalElements()).isEqualTo(1L);
        verify(repository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("search - empty page returns empty content")
    void search_emptyResults() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<ChannelConnectionLogResponse> result = service.search(
                null, null, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("logSuccess - persists a SUCCESS log entry with channel and metadata")
    void logSuccess_persistsLogEntry() {
        when(repository.save(any(ChannelConnectionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.logSuccess(sampleChannel, ChannelConnectionAction.CONNECT, "Connected",
                Map.of("shopDomain", "my-shop"));

        ArgumentCaptor<ChannelConnectionLog> captor = ArgumentCaptor.forClass(ChannelConnectionLog.class);
        verify(repository).save(captor.capture());
        ChannelConnectionLog saved = captor.getValue();

        assertThat(saved.getPlatform()).isEqualTo(PlatformType.SHOPIFY);
        assertThat(saved.getChannel()).isEqualTo(sampleChannel);
        assertThat(saved.getChannelName()).isEqualTo("my-shop");
        assertThat(saved.getAction()).isEqualTo(ChannelConnectionAction.CONNECT);
        assertThat(saved.getStatus()).isEqualTo(ChannelConnectionLogStatus.SUCCESS);
        assertThat(saved.getMessage()).isEqualTo("Connected");
        assertThat(saved.getMetadata()).containsEntry("shopDomain", "my-shop");
    }

    @Test
    @DisplayName("logFailure - persists a FAILED log with channelName from metadata")
    void logFailure_persistsFailureLog() {
        when(repository.save(any(ChannelConnectionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> metadata = Map.of("channelName", "broken-shop", "reason", "expired");
        service.logFailure(PlatformType.LAZADA, ChannelConnectionAction.CONNECT,
                "Connection refused", "401 Unauthorized", metadata);

        ArgumentCaptor<ChannelConnectionLog> captor = ArgumentCaptor.forClass(ChannelConnectionLog.class);
        verify(repository).save(captor.capture());
        ChannelConnectionLog saved = captor.getValue();

        assertThat(saved.getPlatform()).isEqualTo(PlatformType.LAZADA);
        assertThat(saved.getAction()).isEqualTo(ChannelConnectionAction.CONNECT);
        assertThat(saved.getStatus()).isEqualTo(ChannelConnectionLogStatus.FAILED);
        assertThat(saved.getChannelName()).isEqualTo("broken-shop");
        assertThat(saved.getErrorMessage()).isEqualTo("401 Unauthorized");
    }

    @Test
    @DisplayName("logFailure - handles missing channelName in metadata")
    void logFailure_missingChannelName() {
        when(repository.save(any(ChannelConnectionLog.class))).thenAnswer(inv -> inv.getArgument(0));

        service.logFailure(PlatformType.SHOPIFY, ChannelConnectionAction.DISCONNECT,
                "Something failed", "error", null);

        ArgumentCaptor<ChannelConnectionLog> captor = ArgumentCaptor.forClass(ChannelConnectionLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getChannelName()).isNull();
    }
}
