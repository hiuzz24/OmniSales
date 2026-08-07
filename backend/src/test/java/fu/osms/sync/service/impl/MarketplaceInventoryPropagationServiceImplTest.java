package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.service.InventoryAutoPushSyncLogService;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketplaceInventoryPropagationServiceImplTest {

        @Mock
        private ChannelRepository channelRepository;
        @Mock
        private ChannelCredentialRepository credentialRepository;
        @Mock
        private ChannelProductVariantRepository channelProductVariantRepository;
        @Mock
        private MarketplaceWarehouseConsistencyService warehouseConsistencyService;
        @Mock
        private ShopifyInventoryUpdateService shopifyInventoryUpdateService;
        @Mock
        private LazadaInventoryUpdateService lazadaInventoryUpdateService;
        @Mock
        private TikTokInventoryUpdateService tikTokInventoryUpdateService;
        @Mock
        private MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;
        @Mock
        private InventoryAutoPushSyncLogService inventoryAutoPushSyncLogService;

        private MarketplaceInventoryPropagationServiceImpl service;

        @BeforeEach
        void setUp() {
                service = new MarketplaceInventoryPropagationServiceImpl(
                                channelRepository,
                                credentialRepository,
                                channelProductVariantRepository,
                                warehouseConsistencyService,
                                shopifyInventoryUpdateService,
                                lazadaInventoryUpdateService,
                                tikTokInventoryUpdateService,
                                marketplaceStockQuantityResolver,
                                inventoryAutoPushSyncLogService);
        }

        @Test
        void pushAvailableStock_skipsSourceChannelAndContinuesOtherMarketplaces() {
                UUID sourceChannelId = UUID.randomUUID();
                UUID shopifyChannelId = UUID.randomUUID();
                UUID variantId = UUID.randomUUID();
                Channel lazada = connectedChannel(sourceChannelId, PlatformType.LAZADA);
                Channel shopify = connectedChannel(shopifyChannelId, PlatformType.SHOPIFY);

                when(marketplaceStockQuantityResolver.expandVariantIdsBySkuGroup(Set.of(variantId)))
                                .thenReturn(Set.of(variantId));
                when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lazada, shopify));
                when(credentialRepository.findByChannelIdAndConnectionState(any(UUID.class), eq("CONNECTED")))
                                .thenAnswer(invocation -> Optional.of(ChannelCredential.builder()
                                                .channel(sourceChannelId.equals(invocation.getArgument(0)) ? lazada
                                                                : shopify)
                                                .accessToken("token")
                                                .connectionState("CONNECTED")
                                                .build()));
                when(channelProductVariantRepository.findActiveByChannelIdAndVariantIdInWithVariant(
                                any(UUID.class), anyList())).thenAnswer(invocation -> {
                                        UUID chId = invocation.getArgument(0);
                                        if (shopifyChannelId.equals(chId)) {
                                                ChannelProductVariant mapping = new ChannelProductVariant();
                                                mapping.setVariant(ProductVariant.builder().id(variantId).build());
                                                return List.of(mapping);
                                        }
                                        return List.of();
                                });
                when(inventoryAutoPushSyncLogService.start(any(UUID.class), any(Integer.class)))
                                .thenReturn(UUID.randomUUID());

                service.pushAvailableStock(Set.of(variantId), sourceChannelId);

                verify(lazadaInventoryUpdateService, never()).syncChangedSellableStock(
                                eq(sourceChannelId), any(), any(), any());
                verify(shopifyInventoryUpdateService).syncChangedAvailableStock(
                                eq(shopifyChannelId), any(), any(), eq(Set.of(variantId)));
        }

        private Channel connectedChannel(UUID id, PlatformType platform) {
                return Channel.builder()
                                .id(id)
                                .platform(platform)
                                .syncEnabled(true)
                                .build();
        }
}
