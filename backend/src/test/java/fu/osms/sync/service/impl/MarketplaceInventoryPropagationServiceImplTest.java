package fu.osms.sync.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.lazada.service.LazadaInventoryUpdateService;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.shopify.ShopifyInventoryUpdateService;
import fu.osms.sync.tiktok.TikTokInventoryUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceInventoryPropagationServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private MarketplaceWarehouseConsistencyService warehouseConsistencyService;
    @Mock private ShopifyInventoryUpdateService shopifyInventoryUpdateService;
    @Mock private LazadaInventoryUpdateService lazadaInventoryUpdateService;
    @Mock private TikTokInventoryUpdateService tikTokInventoryUpdateService;
    @Mock private MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;
    @Mock private TransactionTemplate transactionTemplate;

    private MarketplaceInventoryPropagationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceInventoryPropagationServiceImpl(
                channelRepository,
                credentialRepository,
                warehouseConsistencyService,
                shopifyInventoryUpdateService,
                lazadaInventoryUpdateService,
                tikTokInventoryUpdateService,
                marketplaceStockQuantityResolver,
                transactionTemplate
        );
    }

    @Test
    void pushAvailableStock_skipsSourceChannelAndContinuesOtherMarketplaces() {
        UUID sourceChannelId = UUID.randomUUID();
        UUID shopifyChannelId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Channel lazada = connectedChannel(sourceChannelId, PlatformType.LAZADA);
        Channel shopify = connectedChannel(shopifyChannelId, PlatformType.SHOPIFY);

        when(marketplaceStockQuantityResolver.expandVariantIdsBySkuGroup(Set.of(variantId))).thenReturn(Set.of(variantId));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lazada, shopify));
        when(credentialRepository.findByChannelIdAndConnectionState(any(UUID.class), eq("CONNECTED")))
                .thenAnswer(invocation -> Optional.of(ChannelCredential.builder()
                        .channel(sourceChannelId.equals(invocation.getArgument(0)) ? lazada : shopify)
                        .accessToken("token")
                        .connectionState("CONNECTED")
                        .build()));

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
