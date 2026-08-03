package fu.osms.sync.service.impl;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionValidator;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.lazada.service.LazadaImportSyncService;
import fu.osms.sync.shopify.ShopifyImportSyncService;
import fu.osms.sync.tiktok.TikTokImportSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelRemoteSyncServiceImpl Tests")
class ChannelRemoteSyncServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelConnectionValidator channelConnectionValidator;
    @Mock private LazadaImportSyncService lazadaImportSyncService;
    @Mock private ShopifyImportSyncService shopifyImportSyncService;
    @Mock private TikTokImportSyncService tikTokImportSyncService;
    @Mock private TransactionTemplate transactionTemplate;

    private ChannelRemoteSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChannelRemoteSyncServiceImpl(
                channelRepository, channelConnectionValidator,
                lazadaImportSyncService, shopifyImportSyncService,
                tikTokImportSyncService, transactionTemplate);
    }

    private Channel channel(UUID id, PlatformType platform, boolean syncEnabled, Map<String, Object> metadata) {
        return Channel.builder()
                .id(id)
                .platform(platform)
                .displayName("Ch-" + id)
                .syncEnabled(syncEnabled)
                .metadata(metadata == null ? null : new HashMap<>(metadata))
                .build();
    }

    private ChannelImportSyncResponse response(PlatformType platform) {
        return ChannelImportSyncResponse.builder()
                .status("SYNCED")
                .productCount(2)
                .variantCount(5)
                .warehouseCount(1)
                .pushedVariantCount(3)
                .build();
    }

    @Test
    @DisplayName("syncRemoteChanges: delegates to Lazada service for Lazada channels")
    void syncRemoteChanges_lazada() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channel(channelId, PlatformType.LAZADA, true, Map.of("accountId", "acc-1"));
        when(channelConnectionValidator.requireConnected(channelId)).thenReturn(ch);
        when(lazadaImportSyncService.syncProductsAndWarehouses(channelId))
                .thenReturn(response(PlatformType.LAZADA));

        ChannelImportSyncResponse result = service.syncRemoteChanges(channelId);

        assertThat(result.getProductCount()).isEqualTo(2);
        verify(lazadaImportSyncService).syncProductsAndWarehouses(channelId);
    }

    @Test
    @DisplayName("syncRemoteChanges: delegates to Shopify service for Shopify channels")
    void syncRemoteChanges_shopify() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channel(channelId, PlatformType.SHOPIFY, true, Map.of("shopDomain", "shop.myshopify.com"));
        when(channelConnectionValidator.requireConnected(channelId)).thenReturn(ch);
        when(shopifyImportSyncService.syncProductsAndInventory(channelId))
                .thenReturn(response(PlatformType.SHOPIFY));

        service.syncRemoteChanges(channelId);

        verify(shopifyImportSyncService).syncProductsAndInventory(channelId);
    }

    @Test
    @DisplayName("syncRemoteChanges: delegates to TikTok service for TikTok channels")
    void syncRemoteChanges_tiktok() {
        UUID channelId = UUID.randomUUID();
        Channel ch = channel(channelId, PlatformType.TIKTOK, true, Map.of("shopCipher", "cipher-1"));
        when(channelConnectionValidator.requireConnected(channelId)).thenReturn(ch);
        when(tikTokImportSyncService.syncProductsAndInventory(channelId))
                .thenReturn(response(PlatformType.TIKTOK));

        service.syncRemoteChanges(channelId);

        verify(tikTokImportSyncService).syncProductsAndInventory(channelId);
    }

    @Test
    @DisplayName("syncAllRemoteChanges: aggregates product/variant counts across successful channels")
    void syncAllRemoteChanges_aggregates() {
        UUID lzId = UUID.randomUUID();
        UUID shId = UUID.randomUUID();
        Channel lz = channel(lzId, PlatformType.LAZADA, true, Map.of("accountId", "acc"));
        Channel sh = channel(shId, PlatformType.SHOPIFY, true, Map.of("shopDomain", "s.myshopify.com"));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lz, sh));
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        UUID triggerId = UUID.randomUUID();
        when(channelConnectionValidator.requireConnected(eq(triggerId))).thenReturn(lz);
        when(channelConnectionValidator.requireConnected(eq(lzId))).thenReturn(lz);
        when(channelConnectionValidator.requireConnected(eq(shId))).thenReturn(sh);
        when(lazadaImportSyncService.syncProductsAndWarehouses(lzId))
                .thenReturn(response(PlatformType.LAZADA));
        when(shopifyImportSyncService.syncProductsAndInventory(shId))
                .thenReturn(response(PlatformType.SHOPIFY));

        ChannelImportSyncResponse result = service.syncAllRemoteChanges(triggerId);

        assertThat(result.getProductCount()).isEqualTo(4);
        assertThat(result.getVariantCount()).isEqualTo(10);
        assertThat(result.getDetails()).hasSize(2);
    }

    @Test
    @DisplayName("syncAllRemoteChanges: marks FAILED status when any channel fails")
    void syncAllRemoteChanges_partialFailure() {
        UUID lzId = UUID.randomUUID();
        Channel lz = channel(lzId, PlatformType.LAZADA, true, Map.of("accountId", "acc"));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lz));
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            var cb = inv.getArgument(0, org.springframework.transaction.support.TransactionCallback.class);
            return cb.doInTransaction(null);
        });
        UUID triggerId = UUID.randomUUID();
        when(channelConnectionValidator.requireConnected(eq(triggerId))).thenReturn(lz);
        when(channelConnectionValidator.requireConnected(eq(lzId))).thenReturn(lz);
        doThrow(new RuntimeException("API down")).when(lazadaImportSyncService).syncProductsAndWarehouses(lzId);

        ChannelImportSyncResponse result = service.syncAllRemoteChanges(triggerId);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("API down");
        assertThat(result.getDetails()).hasSize(1);
    }

    @Test
    @DisplayName("syncAllRemoteChanges: only includes syncEnabled=true and supported platform channels")
    void syncAllRemoteChanges_filters() {
        Channel enabledLazada = channel(UUID.randomUUID(), PlatformType.LAZADA, true, Map.of("accountId", "a"));
        Channel disabledLazada = channel(UUID.randomUUID(), PlatformType.LAZADA, false, Map.of("accountId", "a"));
        Channel manual = channel(UUID.randomUUID(), PlatformType.MANUAL, true, null);
        when(channelRepository.findByDeletedAtIsNull())
                .thenReturn(List.of(enabledLazada, disabledLazada, manual));

        service.syncAllRemoteChanges(UUID.randomUUID());

        // Only Lazada should be processed (manual is filtered, disabled is filtered)
        verify(transactionTemplate, org.mockito.Mockito.times(1)).execute(any());
    }
}