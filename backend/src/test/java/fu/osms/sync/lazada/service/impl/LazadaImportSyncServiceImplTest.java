package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.Category;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.SyncAlertService;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import fu.osms.sync.service.impl.SyncJobProgressTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaImportSyncServiceImplTest {

    @Mock private LazadaAuthorizedApiClient lazadaApiClient;
    @Mock private ChannelTokenService channelTokenService;
    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private SyncAlertService syncAlertService;
    @Mock private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    @Mock private ChannelProductAggregationService channelProductAggregationService;
    @Mock private SyncJobProgressTracker syncJobProgressTracker;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private LazadaImportSyncServiceImpl service;

    private UUID channelId;
    private Channel channel;
    private ChannelCredential credential;

    @BeforeEach
    void setUp() {
        service = new LazadaImportSyncServiceImpl(
                lazadaApiClient,
                channelTokenService,
                objectMapper,
                channelRepository,
                credentialRepository,
                productRepository,
                productVariantRepository,
                productImageRepository,
                categoryRepository,
                channelProductRepository,
                channelProductVariantRepository,
                inventoryItemRepository,
                inventoryTransactionRepository,
                warehouseRepository,
                syncLogRepository,
                syncAlertService,
                marketplaceInventoryPropagationService,
                marketplaceWarehouseConsistencyService,
                channelProductAggregationService,
                syncJobProgressTracker
        );

        channelId = UUID.randomUUID();
        channel = Channel.builder()
                .id(channelId)
                .platform(PlatformType.LAZADA)
                .displayName("Lazada-Import")
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
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — channel platform != LAZADA throws IllegalArgumentException")
    void syncProductsAndWarehouses_wrongPlatform() {
        Channel shopify = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .displayName("Shopify-Channel")
                .build();
        UUID otherId = shopify.getId();
        when(channelRepository.findById(otherId)).thenReturn(Optional.of(shopify));

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(otherId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lazada");

        verify(lazadaApiClient, never()).executeGet(any(UUID.class), anyString(), anyMap());
        verify(credentialRepository, never()).findByChannelIdAndConnectionState(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — channel not found throws AppException(CHANNEL_NOT_FOUND)")
    void syncProductsAndWarehouses_channelNotFound() {
        when(channelRepository.findById(channelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(channelId))
                .isInstanceOf(fu.osms.common.exception.AppException.class)
                .extracting("errorCode").isEqualTo(fu.osms.common.exception.ErrorCode.CHANNEL_NOT_FOUND);

        verify(credentialRepository, never()).findByChannelIdAndConnectionState(any(UUID.class), anyString());
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — missing CONNECTED credential throws IllegalStateException")
    void syncProductsAndWarehouses_noCredential() {
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncProductsAndWarehouses(channelId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token");

        verify(lazadaApiClient, never()).executeGet(any(UUID.class), anyString(), anyMap());
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — happy path persists SyncLog with status SYNCED")
    void syncProductsAndWarehouses_happy() {
        channel.setLastSyncedAt(OffsetDateTime.now().minusDays(1));
        lenient().when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
        lenient().when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        lenient().when(lazadaApiClient.executeGet(eq(channelId), eq("/rc/warehouse/get"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"warehouses\":[{\"id\":\"WH-1\",\"name\":\"Main WH\"}]}}");
        lenient().when(lazadaApiClient.executeGet(eq(channelId), eq("/category/tree/get"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":[]}");
        lenient().when(lazadaApiClient.executeGet(eq(channelId), eq("/products/get"), anyMap()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"products\":[]}}");
        lenient().when(warehouseRepository.findFirstByNameAndDeletedAtIsNull("Main WH"))
                .thenReturn(Optional.of(Warehouse.builder()
                        .id(UUID.randomUUID())
                        .name("Main WH")
                        .address("[LAZADA_WAREHOUSE_CODE=WH-1]")
                        .isActive(true)
                        .build()));
        lenient().when(warehouseRepository.save(any(Warehouse.class)))
                .thenAnswer(i -> i.getArgument(0));
        lenient().when(channelProductRepository.countByChannelIdAndMappingState(channelId, "ACTIVE"))
                .thenReturn(0L);
        lenient().when(channelProductVariantRepository.countActiveByChannelId(channelId))
                .thenReturn(0L);
        // Track SyncLog save so we can assert status
        java.util.concurrent.atomic.AtomicReference<SyncLog> savedLog = new java.util.concurrent.atomic.AtomicReference<>();
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(i -> {
            SyncLog log = i.getArgument(0);
            if (log.getId() == null) log.setId(UUID.randomUUID());
            savedLog.set(log);
            return log;
        });

        var response = service.syncProductsAndWarehouses(channelId);

        assertThat(savedLog.get().getStatus()).isEqualTo(fu.osms.common.enums.SyncStatus.SYNCED);
        assertThat(response.getStatus()).isEqualTo("SYNCED");
        ArgumentCaptor<Map<String, String>> productParams = ArgumentCaptor.forClass(Map.class);
        verify(lazadaApiClient, times(2)).executeGet(eq(channelId), eq("/products/get"), productParams.capture());
        // First call: no "filter" - asks Lazada for normal/active products
        assertThat(productParams.getAllValues().get(0))
                .doesNotContainKey("filter")
                .containsKeys("create_after", "update_after");
        // Second call: filter=inactive to fetch Lazada-inactive products
        assertThat(productParams.getAllValues().get(1))
                .containsEntry("filter", "inactive")
                .containsKeys("create_after", "update_after");
        verify(marketplaceInventoryPropagationService).schedulePushAvailableStock(any(), eq(channelId));
        verify(syncAlertService, never()).notifySyncFailure(any(SyncLog.class));
    }

    @Test
    @DisplayName("syncProductsAndWarehouses — merges normal and inactive Lazada responses without duplicate counts")
    void syncProductsAndWarehouses_mergesNormalAndInactiveResponses() {
        channel.getMetadata().put("lazadaWarehouseCode", "WH-DEFAULT");
        Warehouse masterWarehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Default")
                .isActive(true)
                .build();

        lenient().when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
        lenient().when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(marketplaceWarehouseConsistencyService.resolveAndValidatePrimaryWarehouse(channel))
                .thenReturn(masterWarehouse);
        when(lazadaApiClient.executeGet(eq(channelId), eq("/rc/warehouse/get"), anyMap()))
                .thenReturn("""
                        {"code":"0","data":{"warehouses":[
                          {"warehouseCode":"WH-DEFAULT","isDefault":true,"name":"Default"},
                          {"warehouseCode":"WH-2","name":"Second"}
                        ]}}
                        """);
        when(lazadaApiClient.executeGet(eq(channelId), eq("/category/tree/get"), anyMap()))
                .thenReturn("""
                        {"code":"0","data":[{"category_id":"100","name":"Phones"}]}
                        """);
        when(lazadaApiClient.executeGet(eq(channelId), eq("/products/get"), anyMap()))
                .thenReturn(lazadaProductResponse());

        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) {
                product.setId(UUID.randomUUID());
            }
            if (product.getLowStockThreshold() == null) {
                product.setLowStockThreshold(5);
            }
            return product;
        });
        when(productVariantRepository.findBySkuAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(productVariantRepository.findByProductIdAndSkuAndDeletedAtIsNull(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        when(productVariantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            if (variant.getId() == null) {
                variant.setId(UUID.randomUUID());
            }
            return variant;
        });
        when(categoryRepository.findBySlug("lazada-100")).thenReturn(Optional.empty());
        when(categoryRepository.findFirstByNameIgnoreCase("Phones")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            if (category.getId() == null) {
                category.setId(UUID.randomUUID());
            }
            return category;
        });
        when(channelProductRepository.findByChannelIdAndExternalProductId(channelId, "P-1"))
                .thenReturn(Optional.empty());
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(invocation -> {
            ChannelProduct mapping = invocation.getArgument(0);
            if (mapping.getId() == null) {
                mapping.setId(UUID.randomUUID());
            }
            return mapping;
        });
        when(channelProductAggregationService.normalizeImportedMapping(any(ChannelProduct.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelProductVariantRepository.findByChannelProductIdAndExternalVariantId(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
        when(channelProductVariantRepository.findByChannelProductIdAndVariantId(any(UUID.class), any(UUID.class)))
                .thenReturn(Optional.empty());
        when(channelProductVariantRepository.save(any(ChannelProductVariant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(channelProductRepository.countByChannelIdAndMappingState(channelId, "ACTIVE"))
                .thenReturn(1L);
        when(channelProductVariantRepository.countActiveByChannelId(channelId))
                .thenReturn(1L);
        when(syncLogRepository.save(any(SyncLog.class))).thenAnswer(invocation -> {
            SyncLog log = invocation.getArgument(0);
            if (log.getId() == null) {
                log.setId(UUID.randomUUID());
            }
            return log;
        });

        ChannelImportSyncResponse response = service.syncProductsAndWarehouses(channelId);

        assertThat(response.getProductCount()).isEqualTo(1);
        assertThat(response.getVariantCount()).isEqualTo(1);
        assertThat(response.getWarehouseCount()).isEqualTo(1);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("Phones");

        ArgumentCaptor<Iterable<ProductImage>> imagesCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(productImageRepository, times(2)).saveAll(imagesCaptor.capture());
        List<ProductImage> savedImages = imagesCaptor.getAllValues().stream()
                .flatMap(images -> StreamSupport.stream(images.spliterator(), false))
                .toList();
        assertThat(savedImages)
                .anyMatch(image -> image.getVariant() == null && image.getUrl().equals("https://img/product.jpg"))
                .anyMatch(image -> image.getVariant() != null && image.getUrl().equals("https://img/sku.jpg"));
    }

    private String lazadaProductResponse() {
        return """
                {"code":"0","data":{"total_products":"1","products":[{
                  "item_id":"P-1",
                  "primary_category":"100",
                  "images":"[\\"https://img/product.jpg\\"]",
                  "attributes":{"name":"Imported phone","description":"desc","brand":"Brand"},
                  "status":"Active",
                  "skus":[{
                    "SkuId":"SKU-ID-1",
                    "SellerSku":"SKU-1",
                    "price":100,
                    "quantity":5,
                    "Images":["https://img/sku.jpg","",""]
                  }]
                }]}}
                """;
    }
}
