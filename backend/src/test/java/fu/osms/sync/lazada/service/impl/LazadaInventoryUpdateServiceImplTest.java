package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.token.dto.AccessTokenContext;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaInventoryUpdateServiceImplTest {

    @Mock private LazadaAuthorizedApiClient lazadaApiClient;
    @Mock private ChannelTokenService channelTokenService;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private StockReceiveRepository stockReceiveRepository;
    @Mock private InventoryIssueRepository inventoryIssueRepository;
    @Mock private MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LazadaInventoryUpdateServiceImpl service;

    private UUID channelId;
    private Channel channel;
    private ChannelCredential credential;
    private ProductVariant variant;
    private Warehouse defaultWarehouse;
    private InventoryItem item;
    private ChannelProductVariant mapping;

    @BeforeEach
    void setUp() {
        service = new LazadaInventoryUpdateServiceImpl(
                lazadaApiClient,
                channelTokenService,
                objectMapper,
                credentialRepository,
                channelProductVariantRepository,
                inventoryItemRepository,
                stockReceiveRepository,
                inventoryIssueRepository,
                marketplaceStockQuantityResolver
        );

        channelId = UUID.randomUUID();
        channel = Channel.builder()
                .id(channelId)
                .platform(PlatformType.LAZADA)
                .displayName("Lazada-Test")
                .metadata(new HashMap<>())
                .build();

        credential = ChannelCredential.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .accessToken("access-tok")
                .connectionState("CONNECTED")
                .tokenExpiresAt(OffsetDateTime.now().plusHours(1))
                .refreshTokenExpiresAt(OffsetDateTime.now().plusDays(30))
                .build();

        defaultWarehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Default WH")
                .address("[LAZADA_WAREHOUSE_CODE=MY-WAREHOUSE-CODE]")
                .isActive(true)
                .build();

        Product product = Product.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .sku("PROD-001")
                .build();

        variant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .sku("SKU-001")
                .price(new BigDecimal("199.99"))
                .product(product)
                .build();

        ChannelProduct channelProduct = ChannelProduct.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .build();

        mapping = ChannelProductVariant.builder()
                .id(UUID.randomUUID())
                .channelProduct(channelProduct)
                .variant(variant)
                .externalVariantId("SKU-ID-1")
                .externalSku("SKU-001")
                .build();

        item = InventoryItem.builder()
                .id(UUID.randomUUID())
                .warehouse(defaultWarehouse)
                .variant(variant)
                .quantityOnHand(10)
                .reservedQuantity(2)
                .build();
    }

    @Test
    @DisplayName("syncChangedSellableStock — full baseline: builds a SKU payload and posts to Lazada")
    void syncChangedSellableStock_fullBaseline() throws Exception {
        channel.getMetadata().put("defaultWarehouseId", defaultWarehouse.getId().toString());
        channel.getMetadata().put("lazadaWarehouseCode", "MY-WAREHOUSE-CODE");

        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelTokenService.getValidToken(channelId))
                .thenReturn(new AccessTokenContext(channelId, PlatformType.LAZADA, "access-tok", OffsetDateTime.now().plusSeconds(3600)));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(channelId))
                .thenReturn(List.of(mapping));
        when(inventoryItemRepository.findByVariantIdIn(List.of(variant.getId())))
                .thenReturn(List.of(item));
        when(marketplaceStockQuantityResolver.maxAvailableQuantityForSkuGroup(mapping))
                .thenReturn(8);
        when(lazadaApiClient.executePost(eq(channelId), eq("/product/stock/sellable/update"), anyMap()))
                .thenReturn("{\"code\":\"0\"}");

        LazadaInventorySyncResult result = service.syncChangedSellableStock(
                channelId, null, null, null);

        assertThat(result.affectedProductCount()).isEqualTo(1);
        assertThat(result.pushedVariantCount()).isEqualTo(1);
        assertThat(result.changedWarehouseCount()).isEqualTo(0);
        verify(lazadaApiClient).executePost(eq(channelId), eq("/product/stock/sellable/update"), anyMap());
    }

    @Test
    @DisplayName("syncChangedSellableStock — blank access token raises IllegalStateException before any API call")
    void syncChangedSellableStock_blankToken() {
        credential.setAccessToken("   ");

        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelTokenService.getValidToken(channelId))
                .thenReturn(new AccessTokenContext(channelId, PlatformType.LAZADA, "   ", OffsetDateTime.now().plusSeconds(3600)));

        assertThatThrownBy(() -> service.syncChangedSellableStock(channelId, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");

        verify(lazadaApiClient, never()).executePost(any(UUID.class), any(String.class), anyMap());
    }

    @Test
    @DisplayName("syncChangedSellableStock — missing credential raises IllegalStateException")
    void syncChangedSellableStock_missingCredential() {
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncChangedSellableStock(channelId, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token ket noi");

        verify(lazadaApiClient, never()).executePost(any(UUID.class), any(String.class), anyMap());
    }

    @Test
    @DisplayName("syncChangedSellableStock — changedSince with empty scoped variants returns zero result, no API call")
    void syncChangedSellableStock_changedSinceNoChanges() {
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelTokenService.getValidToken(channelId)).thenReturn(new AccessTokenContext(channelId, PlatformType.LAZADA, "access-tok", OffsetDateTime.now().plusSeconds(3600)));

        LazadaInventorySyncResult result = service.syncChangedSellableStock(
                channelId,
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now(),
                Set.of()
        );

        assertThat(result.pushedVariantCount()).isZero();
        assertThat(result.affectedProductCount()).isZero();
        verify(lazadaApiClient, never()).executePost(any(UUID.class), any(String.class), anyMap());
    }

    @Test
    @DisplayName("syncChangedSellableStock — Lazada API returns non-zero code throws IllegalStateException")
    void syncChangedSellableStock_apiError() {
        channel.getMetadata().put("defaultWarehouseId", defaultWarehouse.getId().toString());
        channel.getMetadata().put("lazadaWarehouseCode", "MY-WAREHOUSE-CODE");

        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelTokenService.getValidToken(channelId)).thenReturn(new AccessTokenContext(channelId, PlatformType.LAZADA, "access-tok", OffsetDateTime.now().plusSeconds(3600)));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(channelId))
                .thenReturn(List.of(mapping));
        when(inventoryItemRepository.findByVariantIdIn(List.of(variant.getId())))
                .thenReturn(List.of(item));
        when(marketplaceStockQuantityResolver.maxAvailableQuantityForSkuGroup(mapping))
                .thenReturn(8);
        when(lazadaApiClient.executePost(eq(channelId), eq("/product/stock/sellable/update"), anyMap()))
                .thenReturn("{\"code\":\"500\",\"message\":\"oops\"}");

        assertThatThrownBy(() -> service.syncChangedSellableStock(channelId, null, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("oops");
    }

    @Test
    @DisplayName("syncChangedSellableStock — no mappings returns early with zero pushed variants")
    void syncChangedSellableStock_noMappings() {
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelTokenService.getValidToken(channelId)).thenReturn(new AccessTokenContext(channelId, PlatformType.LAZADA, "access-tok", OffsetDateTime.now().plusSeconds(3600)));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(channelId))
                .thenReturn(List.of());

        LazadaInventorySyncResult result = service.syncChangedSellableStock(channelId, null, null, null);

        assertThat(result.pushedVariantCount()).isZero();
        verify(lazadaApiClient, never()).executePost(any(UUID.class), any(String.class), anyMap());
    }
}
