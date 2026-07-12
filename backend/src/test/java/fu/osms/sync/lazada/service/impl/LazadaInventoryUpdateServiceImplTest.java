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
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;
import fu.osms.sync.lazada.service.LazadaApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaInventoryUpdateServiceImplTest {

    @Mock private LazadaApiClient lazadaApiClient;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private StockReceiveRepository stockReceiveRepository;
    @Mock private InventoryIssueRepository inventoryIssueRepository;

    private LazadaInventoryUpdateServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Channel lazadaChannel;
    private ChannelCredential credential;
    private ChannelProduct channelProduct;
    private ChannelProductVariant mapping;
    private Product product;
    private ProductVariant variant;
    private Warehouse warehouse;
    private InventoryItem inventoryItem;

    @BeforeEach
    void setUp() {
        service = new LazadaInventoryUpdateServiceImpl(
                lazadaApiClient, objectMapper, credentialRepository,
                channelProductVariantRepository, inventoryItemRepository,
                stockReceiveRepository, inventoryIssueRepository);

        lazadaChannel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Lazada")
                .build();
        credential = ChannelCredential.builder()
                .id(UUID.randomUUID())
                .channel(lazadaChannel)
                .accessToken("acc")
                .connectionState("CONNECTED")
                .tokenExpiresAt(OffsetDateTime.now().plusHours(1))
                .build();
        product = Product.builder().id(UUID.randomUUID()).name("P").build();
        variant = ProductVariant.builder()
                .id(UUID.randomUUID()).product(product).sku("SKU-1").isActive(true).build();
        channelProduct = ChannelProduct.builder()
                .id(UUID.randomUUID()).channel(lazadaChannel).product(product)
                .externalProductId("LP-1").build();
        mapping = ChannelProductVariant.builder()
                .id(UUID.randomUUID()).channelProduct(channelProduct)
                .variant(variant).externalVariantId("LZS-1").externalSku("SKU-1").build();
        warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("WH-1")
                .address("[LAZADA_WAREHOUSE_CODE=HCM-01]")
                .build();
        inventoryItem = InventoryItem.builder()
                .id(UUID.randomUUID())
                .variant(variant)
                .warehouse(warehouse)
                .quantityOnHand(50)
                .reservedQuantity(5)
                .build();
    }

    @Test
    @DisplayName("syncChangedSellableStock — no credential → IllegalStateException")
    void sync_noCredential_throws() {
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncChangedSellableStock(lazadaChannel.getId(), null, null, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chua co token");
    }

    @Test
    @DisplayName("syncChangedSellableStock — blank access_token → IllegalStateException")
    void sync_blankAccessToken_throws() {
        credential.setAccessToken("  ");
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.syncChangedSellableStock(lazadaChannel.getId(), null, null, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
    }

    @Test
    @DisplayName("syncChangedSellableStock — FULL baseline with 1 SKU → sends one batch, returns counts")
    void sync_fullBaseline_happy() {
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(lazadaChannel.getId()))
                .thenReturn(List.of(mapping));
        when(inventoryItemRepository.findByVariantIdIn(List.of(variant.getId())))
                .thenReturn(List.of(inventoryItem));
        when(lazadaApiClient.executePost(eq("/product/stock/sellable/update"), any(), eq("acc"), any()))
                .thenReturn("{\"code\":\"0\"}");
        when(channelProductVariantRepository.save(any(ChannelProductVariant.class)))
                .thenAnswer(i -> i.getArgument(0));

        LazadaInventorySyncResult result = service.syncChangedSellableStock(
                lazadaChannel.getId(), null, null, Set.of());

        assertThat(result.pushedVariantCount()).isEqualTo(1);
        assertThat(result.affectedProductCount()).isEqualTo(1);
        assertThat(result.changedWarehouseCount()).isZero();
    }

    @Test
    @DisplayName("syncChangedSellableStock — incremental with no variant changes → short-circuit, no API call")
    void sync_incremental_noChanges_returnsEmpty() {
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        List<UUID> emptyList = java.util.Collections.emptyList();
        when(stockReceiveRepository.findChangedConfirmedVariantIdsBetween(any(), any())).thenReturn(emptyList);
        when(inventoryIssueRepository.findChangedAppliedVariantIdsBetween(any(), any())).thenReturn(emptyList);
        when(stockReceiveRepository.findChangedConfirmedWarehouseIdsBetween(any(), any())).thenReturn(emptyList);
        when(inventoryIssueRepository.findChangedAppliedWarehouseIdsBetween(any(), any())).thenReturn(emptyList);

        LazadaInventorySyncResult result = service.syncChangedSellableStock(
                lazadaChannel.getId(), OffsetDateTime.now().minusHours(1), null, Set.of());

        assertThat(result.pushedVariantCount()).isZero();
        assertThat(result.affectedProductCount()).isZero();
    }

    @Test
    @DisplayName("syncChangedSellableStock — API error code → IllegalStateException with detail")
    void sync_apiErrorThrows() {
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(lazadaChannel.getId()))
                .thenReturn(List.of(mapping));
        when(inventoryItemRepository.findByVariantIdIn(List.of(variant.getId())))
                .thenReturn(List.of(inventoryItem));
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenReturn("{\"code\":\"500\",\"message\":\"bad payload\"}");

        assertThatThrownBy(() -> service.syncChangedSellableStock(
                lazadaChannel.getId(), null, null, Set.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad payload");
    }

    @Test
    @DisplayName("syncChangedSellableStock — payload contains <MultiWarehouseInventory> when warehouse code exists")
    void sync_payloadContainsWarehouseCode() {
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(lazadaChannel.getId()))
                .thenReturn(List.of(mapping));
        when(inventoryItemRepository.findByVariantIdIn(List.of(variant.getId())))
                .thenReturn(List.of(inventoryItem));
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenReturn("{\"code\":\"0\"}");
        when(channelProductVariantRepository.save(any(ChannelProductVariant.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.syncChangedSellableStock(lazadaChannel.getId(), null, null, Set.of());

        ArgumentCaptor<Map<String, String>> paramsCap = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(lazadaApiClient).executePost(
                eq("/product/stock/sellable/update"), paramsCap.capture(), any(), any());

        String payload = paramsCap.getValue().get("payload");
        assertThat(payload).contains("<Sku>");
        assertThat(payload).contains("<ItemId>LP-1</ItemId>");
        assertThat(payload).contains("<SkuId>LZS-1</SkuId>");
        assertThat(payload).contains("<WarehouseCode>HCM-01</WarehouseCode>");
        assertThat(payload).contains("<MultiWarehouseInventory>");
    }

    @Test
    @DisplayName("syncChangedSellableStock — without warehouse code uses flat <SellableQuantity>")
    void sync_payloadFlatSellableQuantity() {
        warehouse.setAddress("no code here");
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(lazadaChannel.getId()))
                .thenReturn(List.of(mapping));
        when(inventoryItemRepository.findByVariantIdIn(List.of(variant.getId())))
                .thenReturn(List.of(inventoryItem));
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenReturn("{\"code\":\"0\"}");
        when(channelProductVariantRepository.save(any(ChannelProductVariant.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.syncChangedSellableStock(lazadaChannel.getId(), null, null, Set.of());

        ArgumentCaptor<Map<String, String>> paramsCap = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(lazadaApiClient).executePost(
                any(), paramsCap.capture(), any(), any());

        String payload = paramsCap.getValue().get("payload");
        assertThat(payload).contains("<SellableQuantity>45</SellableQuantity>");
        assertThat(payload).doesNotContain("<MultiWarehouseInventory>");
    }

    @Test
    @DisplayName("syncChangedSellableStock — XML-escapes special chars in payload")
    void sync_xmlEscapesSpecialChars() {
        variant.setSku("SKU&<1>");
        mapping.setExternalSku("SKU&<1>");
        when(credentialRepository.findByChannelIdAndConnectionState(lazadaChannel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(channelProductVariantRepository.findActiveByChannelIdWithVariant(lazadaChannel.getId()))
                .thenReturn(List.of(mapping));
        when(inventoryItemRepository.findByVariantIdIn(List.of(variant.getId())))
                .thenReturn(List.of(inventoryItem));
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenReturn("{\"code\":\"0\"}");
        when(channelProductVariantRepository.save(any(ChannelProductVariant.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.syncChangedSellableStock(lazadaChannel.getId(), null, null, Set.of());

        ArgumentCaptor<Map<String, String>> paramsCap = ArgumentCaptor.forClass(Map.class);
        org.mockito.Mockito.verify(lazadaApiClient).executePost(
                any(), paramsCap.capture(), any(), any());
        String payload = paramsCap.getValue().get("payload");

        assertThat(payload).contains("SKU&amp;&lt;1&gt;");
        assertThat(payload).doesNotContain("SKU&<1>");
    }
}
