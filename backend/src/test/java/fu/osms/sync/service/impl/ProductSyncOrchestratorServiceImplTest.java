package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import fu.osms.sync.dto.SyncResult;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.PlatformSyncService;
import fu.osms.sync.service.SyncAlertService;
import fu.osms.sync.service.impl.PlatformSyncServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
@DisplayName("ProductSyncOrchestratorServiceImpl Tests")
class ProductSyncOrchestratorServiceImplTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelConnectionValidator channelConnectionValidator;
    @Mock private PlatformSyncServiceFactory platformSyncServiceFactory;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private SyncAlertService syncAlertService;
    @Mock private PlatformSyncService platformSyncService;

    private ProductSyncOrchestratorServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductSyncOrchestratorServiceImpl(
                productRepository, productVariantRepository, productImageRepository,
                channelProductRepository, channelConnectionValidator,
                platformSyncServiceFactory, syncLogRepository, syncAlertService);
    }

    private Product product(UUID id) {
        return Product.builder().id(id).build();
    }

    private Channel channel(UUID id, PlatformType platform) {
        return Channel.builder().id(id).displayName("Ch-" + id).platform(platform).build();
    }

    private ChannelProduct channelProduct(Product product, Channel channel) {
        return ChannelProduct.builder()
                .id(UUID.randomUUID())
                .product(product)
                .channel(channel)
                .mappingState("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("syncProductToAllChannels: throws AppException when product not found")
    void allChannels_productNotFound() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncProductToAllChannels(id))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("syncProductToAllChannels: returns empty result when no ACTIVE channel mappings exist")
    void allChannels_noMappings() {
        UUID id = UUID.randomUUID();
        when(productRepository.findById(id)).thenReturn(Optional.of(product(id)));
        when(productVariantRepository.findByProductIdAndDeletedAtIsNull(id)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(id)).thenReturn(List.of());
        when(channelProductRepository.findByProductIdAndMappingState(id, "ACTIVE")).thenReturn(List.of());

        SyncResult result = service.syncProductToAllChannels(id);

        assertThat(result.getTotalChannels()).isZero();
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailedCount()).isZero();
    }

    @Test
    @DisplayName("syncProductToAllChannels: counts channel-connection failure without invoking platform service")
    void allChannels_connectionFails() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.LAZADA);
        ChannelProduct cp = channelProduct(p, ch);

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)).thenReturn(List.of());
        when(channelProductRepository.findByProductIdAndMappingState(productId, "ACTIVE")).thenReturn(List.of(cp));
        doThrow(new AppException(fu.osms.common.exception.ErrorCode.INTERNAL_SERVER_ERROR,
                "channel disconnected"))
                .when(channelConnectionValidator).validateConnected(ch);

        SyncResult result = service.syncProductToAllChannels(productId);

        assertThat(result.getTotalChannels()).isEqualTo(1);
        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getDetails().get(0).isSuccess()).isFalse();
        assertThat(result.getDetails().get(0).getErrorMessage()).contains("disconnected");
        verify(platformSyncServiceFactory, never()).getService(any());
    }

    @Test
    @DisplayName("syncProductToAllChannels: marks SYNCED on success and notifies nothing")
    void allChannels_success() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.SHOPIFY);
        ChannelProduct cp = channelProduct(p, ch);

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)).thenReturn(List.of());
        when(channelProductRepository.findByProductIdAndMappingState(productId, "ACTIVE")).thenReturn(List.of(cp));
        when(platformSyncServiceFactory.getService(PlatformType.SHOPIFY)).thenReturn(platformSyncService);
        when(platformSyncService.syncProduct(p, List.of(), List.of(), ch, cp)).thenReturn(true);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.syncProductToAllChannels(productId);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailedCount()).isZero();
        verify(syncAlertService, never()).notifySyncFailure(any());
    }

    @Test
    @DisplayName("syncProductToAllChannels: marks FAILED on platform sync returning false and notifies alert service")
    void allChannels_platformReturnsFalse() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.LAZADA);
        ChannelProduct cp = channelProduct(p, ch);
        cp.setLastSyncError("api rate limit");

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)).thenReturn(List.of());
        when(channelProductRepository.findByProductIdAndMappingState(productId, "ACTIVE")).thenReturn(List.of(cp));
        when(platformSyncServiceFactory.getService(PlatformType.LAZADA)).thenReturn(platformSyncService);
        when(platformSyncService.syncProduct(p, List.of(), List.of(), ch, cp)).thenReturn(false);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.syncProductToAllChannels(productId);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(result.getDetails().get(0).getErrorMessage()).isEqualTo("api rate limit");
        verify(syncAlertService).notifySyncFailure(any());
    }

    @Test
    @DisplayName("syncProductToAllChannels: catches platform exception, marks FAILED, persists channelProduct error")
    void allChannels_platformThrows() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.TIKTOK);
        ChannelProduct cp = channelProduct(p, ch);

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)).thenReturn(List.of());
        when(channelProductRepository.findByProductIdAndMappingState(productId, "ACTIVE")).thenReturn(List.of(cp));
        when(platformSyncServiceFactory.getService(PlatformType.TIKTOK)).thenReturn(platformSyncService);
        when(platformSyncService.syncProduct(p, List.of(), List.of(), ch, cp))
                .thenThrow(new RuntimeException("network error"));
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.syncProductToAllChannels(productId);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(cp.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(cp.getLastSyncError()).isEqualTo("network error");
        verify(channelProductRepository).save(cp);
    }

    @Test
    @DisplayName("syncProductToChannel: throws AppException when mapping is not ACTIVE")
    void singleChannel_inactiveMapping() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.SHOPIFY);
        ChannelProduct cp = channelProduct(p, ch);
        cp.setMappingState("PAUSED");

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(channelProductRepository.findByProductIdAndChannelId(productId, channelId))
                .thenReturn(Optional.of(cp));

        assertThatThrownBy(() -> service.syncProductToChannel(productId, channelId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("syncProductToChannel: throws AppException when channel connection validation fails")
    void singleChannel_notConnected() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.LAZADA);
        ChannelProduct cp = channelProduct(p, ch);

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(channelProductRepository.findByProductIdAndChannelId(productId, channelId))
                .thenReturn(Optional.of(cp));
        doThrow(new AppException(fu.osms.common.exception.ErrorCode.INTERNAL_SERVER_ERROR, "not connected"))
                .when(channelConnectionValidator).validateConnected(ch);

        assertThatThrownBy(() -> service.syncProductToChannel(productId, channelId))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("syncProductToChannel: success path returns detail with success=true")
    void singleChannel_success() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.SHOPIFY);
        ChannelProduct cp = channelProduct(p, ch);

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(channelProductRepository.findByProductIdAndChannelId(productId, channelId))
                .thenReturn(Optional.of(cp));
        when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)).thenReturn(List.of());
        when(platformSyncServiceFactory.getService(PlatformType.SHOPIFY)).thenReturn(platformSyncService);
        when(platformSyncService.syncProduct(p, List.of(), List.of(), ch, cp)).thenReturn(true);
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.syncProductToChannel(productId, channelId);

        assertThat(result.getTotalChannels()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getDetails().get(0).isSuccess()).isTrue();
    }

    @Test
    @DisplayName("syncProductToChannel: catches platform exception and persists channelProduct error")
    void singleChannel_platformThrows() {
        UUID productId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Product p = product(productId);
        Channel ch = channel(channelId, PlatformType.LAZADA);
        ChannelProduct cp = channelProduct(p, ch);

        when(productRepository.findById(productId)).thenReturn(Optional.of(p));
        when(channelProductRepository.findByProductIdAndChannelId(productId, channelId))
                .thenReturn(Optional.of(cp));
        when(productVariantRepository.findByProductIdAndDeletedAtIsNull(productId)).thenReturn(List.of());
        when(productImageRepository.findByProductIdOrderByIsPrimaryDescSortOrderAsc(productId)).thenReturn(List.of());
        when(platformSyncServiceFactory.getService(PlatformType.LAZADA)).thenReturn(platformSyncService);
        when(platformSyncService.syncProduct(p, List.of(), List.of(), ch, cp))
                .thenThrow(new RuntimeException("kaboom"));
        when(syncLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SyncResult result = service.syncProductToChannel(productId, channelId);

        assertThat(result.getFailedCount()).isEqualTo(1);
        assertThat(cp.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(cp.getLastSyncError()).isEqualTo("kaboom");
        verify(channelProductRepository).save(cp);
        ArgumentCaptor<fu.osms.sync.entity.SyncLog> captor = ArgumentCaptor.forClass(fu.osms.sync.entity.SyncLog.class);
        verify(syncLogRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SyncStatus.FAILED);
    }
}
