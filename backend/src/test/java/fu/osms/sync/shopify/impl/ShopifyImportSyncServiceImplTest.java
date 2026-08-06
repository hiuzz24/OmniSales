package fu.osms.sync.shopify.impl;

import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductImageRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.PlatformCatalogOwnershipPolicy;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import fu.osms.sync.service.impl.SyncJobProgressTracker;
import fu.osms.sync.shopify.ShopifyApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyImportSyncServiceImpl Smoke Tests")
class ShopifyImportSyncServiceImplTest {

    @Mock private ShopifyApiClient shopifyApiClient;
    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductImageRepository productImageRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    @Mock private PlatformCatalogOwnershipPolicy catalogOwnershipPolicy;
    @Mock private ChannelProductAggregationService channelProductAggregationService;
    @Mock private SyncJobProgressTracker syncJobProgressTracker;

    private ShopifyImportSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopifyImportSyncServiceImpl(
                shopifyApiClient, channelRepository, credentialRepository,
                channelProductRepository, channelProductVariantRepository,
                productRepository, productImageRepository, productVariantRepository,
                categoryRepository, warehouseRepository, inventoryItemRepository, inventoryTransactionRepository,
                syncLogRepository, marketplaceInventoryPropagationService,
                marketplaceWarehouseConsistencyService, catalogOwnershipPolicy,
                channelProductAggregationService, syncJobProgressTracker);
    }

    @Test
    @DisplayName("Service can be constructed (smoke test verifying dependency wiring)")
    void constructed() {
        assertThat(service).isNotNull();
    }
}