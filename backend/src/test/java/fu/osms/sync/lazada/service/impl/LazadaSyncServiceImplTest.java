package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.token.exception.PlatformAccessTokenExpiredException;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.lazada.dto.LazadaMigratedImages;
import fu.osms.sync.lazada.dto.LazadaProductConfig;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.lazada.service.LazadaImageService;
import fu.osms.sync.lazada.service.LazadaPayloadBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaSyncServiceImplTest {

    @Mock private LazadaAuthorizedApiClient lazadaApiClient;
    @Mock private LazadaImageService lazadaImageService;
    @Mock private LazadaPayloadBuilder lazadaPayloadBuilder;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private ProductChannelConfigService productChannelConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LazadaSyncServiceImpl service;

    private Channel channel;
    private ChannelProduct channelProduct;
    private Product product;
    private ProductVariant variant;
    private LazadaProductConfig config;

    @BeforeEach
    void setUp() {
        service = new LazadaSyncServiceImpl(
                lazadaApiClient,
                lazadaImageService,
                lazadaPayloadBuilder,
                channelProductRepository,
                channelProductVariantRepository,
                objectMapper,
                productChannelConfigService
        );

        channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Lazada VN")
                .metadata(new HashMap<>())
                .build();

        product = validProduct();
        variant = validVariant(product);
        channelProduct = ChannelProduct.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .product(product)
                .syncStatus(SyncStatus.PENDING)
                .metadata(new HashMap<>())
                .build();

        config = LazadaProductConfig.builder()
                .categoryId("100001")
                .categoryName("Apparel")
                .brandId("999")
                .brandName("TestBrand")
                .attributes(new HashMap<>())
                .variantAttributeBindings(new HashMap<>())
                .variantAttributeValueMappings(new HashMap<>())
                .build();

        channelProduct.getMetadata().put("platformConfig", Map.of(
                "categoryId", "100001",
                "categoryName", "Apparel",
                "brandId", "999",
                "brandName", "TestBrand"
        ));
    }

    @Test
    @DisplayName("syncProduct — new product: calls /product/create, stores externalProductId, sets SYNCED")
    void syncProduct_new_happy() throws Exception {
        List<ProductImage> images = List.of();
        when(productChannelConfigService.isReady(channelProduct)).thenReturn(true);
        when(lazadaImageService.migrateImages(anyList(), eq(channel.getId())))
                .thenReturn(new LazadaMigratedImages(List.of("https://laz-img/1"), Map.of()));
        when(lazadaPayloadBuilder.buildPayload(
                any(Product.class), anyList(), any(LazadaMigratedImages.class),
                anyMap(), any(LazadaProductConfig.class), eq(true)))
                .thenReturn("<Request>create</Request>");
        when(lazadaApiClient.executePost(eq(channel.getId()), eq("/product/create"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"item_id\":\"LP-12345\",\"sku_list\":[{\"seller_sku\":\"SKU-001\",\"sku_id\":\"SKU-ID-1\"}]}}");
        when(channelProductVariantRepository.findByChannelProductIdAndVariantId(channelProduct.getId(), variant.getId()))
                .thenReturn(Optional.empty());
        when(channelProductVariantRepository.save(any(ChannelProductVariant.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = service.syncProduct(product, List.of(variant), images, channel, channelProduct);

        assertThat(ok).isTrue();
        assertThat(channelProduct.getExternalProductId()).isEqualTo("LP-12345");
        assertThat(channelProduct.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(channelProduct.getLastSyncError()).isNull();
        verify(channelProductRepository).save(channelProduct);

        ArgumentCaptor<ChannelProductVariant> cpvCap = ArgumentCaptor.forClass(ChannelProductVariant.class);
        verify(channelProductVariantRepository).save(cpvCap.capture());
        assertThat(cpvCap.getValue().getExternalVariantId()).isEqualTo("SKU-ID-1");
        assertThat(cpvCap.getValue().getExternalSku()).isEqualTo("SKU-001");
        assertThat(cpvCap.getValue().getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
    }

    @Test
    @DisplayName("syncProduct — update existing product: calls /product/update with resolved SKU map")
    void syncProduct_update_happy() throws Exception {
        channelProduct.setExternalProductId("LP-EXISTING");
        ChannelProductVariant existingMapping = ChannelProductVariant.builder()
                .id(UUID.randomUUID())
                .channelProduct(channelProduct)
                .variant(variant)
                .externalVariantId("SKU-ID-OLD")
                .externalSku("SKU-001")
                .syncStatus(SyncStatus.SYNCED)
                .build();
        List<ProductImage> images = List.of();

        when(productChannelConfigService.isReady(channelProduct)).thenReturn(true);
        when(lazadaImageService.migrateImages(anyList(), eq(channel.getId())))
                .thenReturn(new LazadaMigratedImages(List.of(), Map.of()));
        when(lazadaPayloadBuilder.buildPayload(
                any(Product.class), anyList(), any(LazadaMigratedImages.class),
                anyMap(), any(LazadaProductConfig.class), eq(false)))
                .thenReturn("<Request>update</Request>");
        when(lazadaApiClient.executePost(eq(channel.getId()), eq("/product/update"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"item_id\":\"LP-EXISTING\"}}");
        when(channelProductVariantRepository.findByChannelProductId(channelProduct.getId()))
                .thenReturn(List.of(existingMapping));
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = service.syncProduct(product, List.of(variant), images, channel, channelProduct);

        assertThat(ok).isTrue();
        verify(lazadaApiClient).executePost(eq(channel.getId()), eq("/product/update"), anyMap());
        verify(lazadaPayloadBuilder).buildPayload(
                any(Product.class), anyList(), any(LazadaMigratedImages.class),
                anyMap(), any(LazadaProductConfig.class), eq(false));
    }

    @Test
    @DisplayName("syncProduct — not ready config: IllegalStateException wrapped in RuntimeException → FAILED status")
    void syncProduct_notReady() {
        when(productChannelConfigService.isReady(channelProduct)).thenReturn(false);
        when(productChannelConfigService.configurationError(channelProduct)).thenReturn("Missing Lazada brand configuration");

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing Lazada brand");

        verify(lazadaApiClient, never()).executePost(any(UUID.class), anyString(), anyMap());
        assertThat(channelProduct.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(channelProduct.getLastSyncError()).contains("Missing Lazada brand");
    }

    @Test
    @DisplayName("syncProduct — missing package weight fails validation and marks FAILED")
    void syncProduct_missingWeight() {
        product.setWeightGrams(null);
        when(productChannelConfigService.isReady(channelProduct)).thenReturn(true);

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing package weight");

        verify(lazadaApiClient, never()).executePost(any(UUID.class), anyString(), anyMap());
        assertThat(channelProduct.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(channelProduct.getLastSyncError()).contains("Missing package weight");
    }

    @Test
    @DisplayName("syncProduct — Lazada API error throws RuntimeException with detail message and sets FAILED")
    void syncProduct_apiError() throws Exception {
        List<ProductImage> images = List.of();
        when(productChannelConfigService.isReady(channelProduct)).thenReturn(true);
        when(lazadaImageService.migrateImages(anyList(), eq(channel.getId())))
                .thenReturn(new LazadaMigratedImages(List.of(), Map.of()));
        when(lazadaPayloadBuilder.buildPayload(any(), any(), any(LazadaMigratedImages.class), any(), any(), eq(true)))
                .thenReturn("<Request>x</Request>");
        when(lazadaApiClient.executePost(any(UUID.class), eq("/product/create"), anyMap()))
                .thenReturn("{\"code\":\"500\",\"message\":\"Invalid payload\",\"detail\":[{\"field\":\"price\",\"message\":\"required\"}]}");
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), images, channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid payload")
                .hasMessageContaining("price");

        assertThat(channelProduct.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(channelProduct.getLastSyncError()).contains("Invalid payload");
    }

    @Test
    @DisplayName("syncProduct — missing brand config rejects with IllegalStateException")
    void syncProduct_missingBrand() {
        channelProduct.getMetadata().put("platformConfig", Map.of(
                "categoryId", "100001",
                "categoryName", "Apparel"
        ));
        when(productChannelConfigService.isReady(channelProduct)).thenReturn(true);

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing Lazada brand");

        verify(lazadaApiClient, never()).executePost(any(UUID.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("syncProduct — PlatformAccessTokenExpiredException bubbles through (no FAILED status set, no save)")
    void syncProduct_tokenExpiredBubbles() throws Exception {
        List<ProductImage> images = List.of();
        when(productChannelConfigService.isReady(channelProduct)).thenReturn(true);
        when(lazadaImageService.migrateImages(anyList(), eq(channel.getId())))
                .thenReturn(new LazadaMigratedImages(List.of(), Map.of()));
        when(lazadaPayloadBuilder.buildPayload(any(), any(), any(LazadaMigratedImages.class), any(), any(), eq(true)))
                .thenReturn("<Request>x</Request>");
        when(lazadaApiClient.executePost(any(UUID.class), eq("/product/create"), anyMap()))
                .thenThrow(new PlatformAccessTokenExpiredException("expired"));

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), images, channel, channelProduct))
                .isInstanceOf(PlatformAccessTokenExpiredException.class);

        verify(channelProductRepository, never()).save(any(ChannelProduct.class));
        assertThat(channelProduct.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
    }

    private Product validProduct() {
        Product p = Product.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .sku("TST-001")
                .description("Lorem ipsum")
                .brand("TestBrand")
                .unit("pcs")
                .weightGrams(800)
                .attributes(new HashMap<>())
                .build();
        Map<String, Object> attrs = p.getAttributes();
        attrs.put("packageLengthCm", "20");
        attrs.put("packageWidthCm", "15");
        attrs.put("packageHeightCm", "10");
        return p;
    }

    private ProductVariant validVariant(Product p) {
        return ProductVariant.builder()
                .id(UUID.randomUUID())
                .product(p)
                .sku("SKU-001")
                .name("Default")
                .price(new BigDecimal("199.99"))
                .isActive(true)
                .build();
    }
}
