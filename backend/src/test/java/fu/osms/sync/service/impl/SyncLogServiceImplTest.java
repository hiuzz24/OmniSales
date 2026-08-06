package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.InventoryTransactionDTOMapper;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.sync.dto.SyncLogResponse;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.mapper.SyncLogMapper;
import fu.osms.sync.repository.SyncLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SyncLogServiceImpl - Unit Tests")
class SyncLogServiceImplTest {

    @Mock
    private SyncLogRepository syncLogRepository;

    @Mock
    private SyncLogMapper syncLogMapper;

    @Mock
    private InventoryTransactionRepository inventoryTransactionRepository;

    @Mock
    private InventoryTransactionDTOMapper inventoryTransactionDTOMapper;

    @InjectMocks
    private SyncLogServiceImpl syncLogService;

    private UUID syncLogId;
    private UUID channelId;
    private SyncLog sampleSyncLog;
    private Channel sampleChannel;

    @BeforeEach
    void setUp() {
        syncLogId = UUID.randomUUID();
        channelId = UUID.randomUUID();

        sampleChannel = Channel.builder()
                .id(channelId)
                .platform(PlatformType.SHOPIFY)
                .displayName("Test Shop")
                .build();

        sampleSyncLog = SyncLog.builder()
                .id(syncLogId)
                .channel(sampleChannel)
                .jobType("PRODUCT_SYNC")
                .status(SyncStatus.SYNCED)
                .startedAt(OffsetDateTime.now().minusMinutes(10))
                .completedAt(OffsetDateTime.now())
                .totalItems(5)
                .successCount(5)
                .failCount(0)
                .build();
    }

    @Test
    @DisplayName("search - returns paginated logs")
    void search_returnsPaginatedLogs() {
        SyncLogResponse response = SyncLogResponse.builder()
                .id(syncLogId)
                .status(SyncStatus.SYNCED)
                .build();

        Page<SyncLog> page = new PageImpl<>(List.of(sampleSyncLog));

        when(syncLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(syncLogMapper.toResponse(any(SyncLog.class))).thenReturn(response);
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceIdWithDetails(any(), any())).thenReturn(List.of());

        PageResponse<SyncLogResponse> result = syncLogService.search(null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(syncLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search - with status filter")
    void search_withStatusFilter() {
        SyncLogResponse response = SyncLogResponse.builder()
                .id(syncLogId)
                .status(SyncStatus.SYNCED)
                .build();

        Page<SyncLog> page = new PageImpl<>(List.of(sampleSyncLog));

        when(syncLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(syncLogMapper.toResponse(any(SyncLog.class))).thenReturn(response);
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceIdWithDetails(any(), any())).thenReturn(List.of());

        PageResponse<SyncLogResponse> result = syncLogService.search(SyncStatus.SYNCED, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(syncLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search - with channelId filter")
    void search_withChannelIdFilter() {
        SyncLogResponse response = SyncLogResponse.builder()
                .id(syncLogId)
                .status(SyncStatus.SYNCED)
                .build();

        Page<SyncLog> page = new PageImpl<>(List.of(sampleSyncLog));

        when(syncLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(syncLogMapper.toResponse(any(SyncLog.class))).thenReturn(response);
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceIdWithDetails(any(), any())).thenReturn(List.of());

        PageResponse<SyncLogResponse> result = syncLogService.search(null, channelId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(syncLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search - with both filters")
    void search_withBothFilters() {
        SyncLogResponse response = SyncLogResponse.builder()
                .id(syncLogId)
                .status(SyncStatus.SYNCED)
                .build();

        Page<SyncLog> page = new PageImpl<>(List.of(sampleSyncLog));

        when(syncLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(syncLogMapper.toResponse(any(SyncLog.class))).thenReturn(response);
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceIdWithDetails(any(), any())).thenReturn(List.of());

        PageResponse<SyncLogResponse> result = syncLogService.search(SyncStatus.SYNCED, channelId, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        verify(syncLogRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("search - returns empty when no results")
    void search_returnsEmptyWhenNoResults() {
        Page<SyncLog> emptyPage = new PageImpl<>(List.of());

        when(syncLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(emptyPage);

        PageResponse<SyncLogResponse> result = syncLogService.search(null, null, 0, 10);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isEqualTo(0);
    }

    @Test
    @DisplayName("toResponseWithInventoryChanges - maps correctly")
    void toResponseWithInventoryChanges_mapsCorrectly() {
        InventoryTransactionDTO txnDto = InventoryTransactionDTO.builder()
                .id(UUID.randomUUID())
                .type(InvTxnType.IMPORT)
                .build();

        SyncLogResponse response = SyncLogResponse.builder()
                .id(syncLogId)
                .status(SyncStatus.SYNCED)
                .inventoryChanges(List.of(txnDto))
                .build();

        Page<SyncLog> page = new PageImpl<>(List.of(sampleSyncLog));

        when(syncLogRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(syncLogMapper.toResponse(any(SyncLog.class))).thenReturn(response);
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceIdWithDetails(any(), any())).thenReturn(List.of());

        PageResponse<SyncLogResponse> result = syncLogService.search(null, null, 0, 10);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getInventoryChanges()).isNotNull();
    }
}
