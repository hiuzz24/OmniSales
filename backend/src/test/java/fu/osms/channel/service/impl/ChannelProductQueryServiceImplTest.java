package fu.osms.channel.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.service.ChannelProductQueryService;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelProductQueryServiceImpl Tests")
class ChannelProductQueryServiceImplTest {

    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ProductChannelConfigService productChannelConfigService;

    private ChannelProductQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChannelProductQueryServiceImpl(channelProductRepository, productChannelConfigService);
    }

    private ChannelProduct mapping(Product product, Channel channel, String mappingState,
                                   String externalProductId, Map<String, Object> metadata) {
        return ChannelProduct.builder()
                .id(UUID.randomUUID())
                .product(product)
                .channel(channel)
                .mappingState(mappingState)
                .externalProductId(externalProductId)
                .metadata(metadata == null ? new HashMap<>() : metadata)
                .syncStatus(SyncStatus.SYNCED)
                .lastSyncedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("getProductChannels returns empty map when productIds is null or empty")
    void getProductChannels_emptyInputs() {
        assertThat(service.getProductChannels(null)).isEmpty();
        assertThat(service.getProductChannels(List.of())).isEmpty();
    }

    @Test
    @DisplayName("getProductChannels groups unique channel-platform names by product id and skips mappings without externalProductId")
    void getProductChannels_groupsByProduct() {
        UUID productA = UUID.randomUUID();
        UUID productB = UUID.randomUUID();
        Channel shopify = Channel.builder().id(UUID.randomUUID()).platform(PlatformType.SHOPIFY).displayName("Shop1").build();
        Channel lazada = Channel.builder().id(UUID.randomUUID()).platform(PlatformType.LAZADA).displayName("Laz1").build();
        Channel tiktok = Channel.builder().id(UUID.randomUUID()).platform(PlatformType.TIKTOK).displayName("TT1").build();

        Product prodA = Product.builder().id(productA).name("A").build();
        Product prodB = Product.builder().id(productB).name("B").build();

        ChannelProduct m1 = mapping(prodA, shopify, "ACTIVE", "ext-shop-1", null);
        ChannelProduct m2 = mapping(prodA, lazada, "ACTIVE", "ext-laz-1", null);
        ChannelProduct m3 = mapping(prodB, tiktok, "ACTIVE", "ext-tt-1", null);
        // Repo already filters by ACTIVE; this one simulates a missing externalProductId which the service must skip.
        ChannelProduct noExtId = mapping(prodA, tiktok, "ACTIVE", null, null);

        when(channelProductRepository.findByProductIdInAndMappingState(anyCollection(), any()))
                .thenReturn(List.of(m1, m2, m3, noExtId));

        Map<UUID, List<String>> result = service.getProductChannels(List.of(productA, productB));

        assertThat(result).hasSize(2);
        assertThat(result.get(productA)).containsExactlyInAnyOrder("SHOPIFY", "LAZADA");
        assertThat(result.get(productB)).containsExactly("TIKTOK");
    }

    @Test
    @DisplayName("getProductChannelIds returns unique channel id list grouped by product id")
    void getProductChannelIds_groupsByProduct() {
        UUID productA = UUID.randomUUID();
        UUID channelShopId = UUID.randomUUID();
        UUID channelLazId = UUID.randomUUID();
        Product prodA = Product.builder().id(productA).name("A").build();

        Channel shop = Channel.builder().id(channelShopId).platform(PlatformType.SHOPIFY).displayName("s").build();
        Channel laz = Channel.builder().id(channelLazId).platform(PlatformType.LAZADA).displayName("l").build();

        ChannelProduct m1 = mapping(prodA, shop, "ACTIVE", "ext-s", null);
        ChannelProduct m2 = mapping(prodA, laz, "ACTIVE", "ext-l", null);
        when(channelProductRepository.findByProductIdInAndMappingState(anyCollection(), any()))
                .thenReturn(List.of(m1, m2));

        Map<UUID, List<UUID>> result = service.getProductChannelIds(List.of(productA));

        assertThat(result.get(productA)).containsExactlyInAnyOrder(channelShopId, channelLazId);
    }

    @Test
    @DisplayName("getProductChannelIds returns empty map for null/empty input")
    void getProductChannelIds_emptyInput() {
        assertThat(service.getProductChannelIds(null)).isEmpty();
        assertThat(service.getProductChannelIds(List.of())).isEmpty();
    }

    @Test
    @DisplayName("getProductChannelSyncs delegates ready/configured metadata to ProductChannelConfigService")
    void getProductChannelSyncs_enrichesFromConfigService() {
        UUID productA = UUID.randomUUID();
        Channel channel = Channel.builder().id(UUID.randomUUID()).platform(PlatformType.SHOPIFY).displayName("MyShop").build();
        Product prodA = Product.builder().id(productA).name("A").build();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("platformConfig", Map.of("readyToSync", true));

        ChannelProduct m1 = mapping(prodA, channel, "ACTIVE", "ext-1", metadata);
        when(channelProductRepository.findByProductIdInAndMappingState(anyCollection(), any()))
                .thenReturn(List.of(m1));
        when(productChannelConfigService.isReady(m1)).thenReturn(true);
        when(productChannelConfigService.configurationError(m1)).thenReturn(null);

        Map<UUID, List<fu.osms.channel.dto.response.ChannelSyncResponse>> result =
                service.getProductChannelSyncs(List.of(productA));

        assertThat(result).containsKey(productA);
        assertThat(result.get(productA)).hasSize(1);
        var sync = result.get(productA).get(0);
        assertThat(sync.getChannelName()).isEqualTo("MyShop");
        assertThat(sync.getPlatform()).isEqualTo("SHOPIFY");
        assertThat(sync.getReadyToSync()).isTrue();
        assertThat(sync.getConfigurationError()).isNull();
        assertThat(sync.getPlatformConfig()).containsEntry("readyToSync", true);
    }

    @Test
    @DisplayName("getProductChannelSyncs returns empty map for null/empty input")
    void getProductChannelSyncs_emptyInput() {
        assertThat(service.getProductChannelSyncs(null)).isEmpty();
        assertThat(service.getProductChannelSyncs(List.of())).isEmpty();
    }
}
