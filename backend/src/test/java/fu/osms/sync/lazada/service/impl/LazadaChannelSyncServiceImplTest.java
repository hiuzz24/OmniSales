package fu.osms.sync.lazada.service.impl;

import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.lazada.dto.LazadaSyncTask;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.SyncAlertService;
import fu.osms.sync.service.impl.PlatformSyncServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaChannelSyncServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private LazadaInventoryUpdateService lazadaInventoryUpdateService;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private PlatformSyncServiceFactory platformSyncServiceFactory;
    @Mock private SyncAlertService syncAlertService;
    @Mock private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    private LazadaChannelSyncServiceImpl service;

    private Channel lazadaChannel;

    @BeforeEach
    void setUp() {
        service = new LazadaChannelSyncServiceImpl(
                channelRepository, channelProductRepository, channelProductVariantRepository,
                syncLogRepository, lazadaInventoryUpdateService,
                productVariantRepository, productImageRepository,
                platformSyncServiceFactory, syncAlertService, marketplaceWarehouseConsistencyService);

        lazadaChannel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Lazada VN")
                .status("ACTIVE")
                .build();
    }

    private void stubSyncLogPersist() {
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(i -> {
            SyncLog log = i.getArgument(0);
            if (log.getId() == null) log.setId(UUID.randomUUID());
            return log;
        });
    }

    @Test
    @DisplayName("syncLocalChanges — channel not found → AppException CHANNEL_NOT_FOUND")
    void sync_channelNotFound() {
        UUID fake = UUID.randomUUID();
        when(channelRepository.findById(fake)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncLocalChanges(new LazadaSyncTask(fake, "manual")))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("syncLocalChanges — channel deleted → AppException CHANNEL_NOT_FOUND")
    void sync_channelDeleted() {
        lazadaChannel.setDeletedAt(java.time.OffsetDateTime.now());
        when(channelRepository.findById(lazadaChannel.getId())).thenReturn(Optional.of(lazadaChannel));

        assertThatThrownBy(() -> service.syncLocalChanges(new LazadaSyncTask(lazadaChannel.getId(), "manual")))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("syncLocalChanges — wrong platform (SHOPIFY) → IllegalArgumentException")
    void sync_wrongPlatform() {
        lazadaChannel.setPlatform(PlatformType.SHOPIFY);
        when(channelRepository.findById(lazadaChannel.getId())).thenReturn(Optional.of(lazadaChannel));

        assertThatThrownBy(() -> service.syncLocalChanges(new LazadaSyncTask(lazadaChannel.getId(), "manual")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lazada");
    }

    @Test
    @DisplayName("syncLocalChanges — empty product set → SKIPPED summary with counts all zero")
    void sync_noChanges() {
        stubSyncLogPersist();
        when(channelRepository.findById(lazadaChannel.getId())).thenReturn(Optional.of(lazadaChannel));
        when(channelProductRepository.findActiveByChannelIdWithProduct(lazadaChannel.getId()))
                .thenReturn(List.of());
        when(channelProductRepository.countByChannelIdAndMappingState(eq(lazadaChannel.getId()), anyString())).thenReturn(0L);
        when(channelProductVariantRepository.countActiveByChannelId(lazadaChannel.getId())).thenReturn(0L);
        lenient().when(lazadaInventoryUpdateService.syncChangedSellableStock(any(), any(), any(), any()))
                .thenReturn(new LazadaInventorySyncResult(0, 0, 0));
        when(channelRepository.save(any(Channel.class))).thenAnswer(i -> i.getArgument(0));

        ChannelImportSyncResponse resp = service.syncLocalChanges(new LazadaSyncTask(lazadaChannel.getId(), "manual"));

        assertThat(resp.getProductCount()).isZero();
        assertThat(resp.getPushedVariantCount()).isZero();
        assertThat(resp.getWarehouseCount()).isZero();
        assertThat(resp.getStatus()).isEqualTo("SYNCED");
    }
}
