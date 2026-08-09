package fu.osms.sync.tiktok.impl;

import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import fu.osms.sync.service.PlatformCatalogOwnershipPolicy;
import fu.osms.sync.service.impl.ChannelProductAggregationService;
import fu.osms.sync.service.impl.SyncJobProgressTracker;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokProductDetailEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokImportSyncServiceImpl Smoke Tests")
class TikTokImportSyncServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private SyncLogRepository syncLogRepository;
    @Mock private TikTokAuthorizedApiClient tikTokApiClient;
    @Mock private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    @Mock private ChannelProductAggregationService channelProductAggregationService;
    @Mock private SyncJobProgressTracker syncJobProgressTracker;
    @Mock private TikTokProductDetailEnrichmentService tikTokProductDetailEnrichmentService;
    @Mock private PlatformCatalogOwnershipPolicy catalogOwnershipPolicy;

    private TikTokImportSyncServiceImpl service;

//    @BeforeEach
//    void setUp() {
//        service = new TikTokImportSyncServiceImpl(
//                channelRepository, channelProductRepository, channelProductVariantRepository,
//                productRepository, productVariantRepository, warehouseRepository,
//                inventoryItemRepository, syncLogRepository, tikTokApiClient,
//                marketplaceInventoryPropagationService, marketplaceWarehouseConsistencyService,
//                channelProductAggregationService, syncJobProgressTracker,
//                tikTokProductDetailEnrichmentService, catalogOwnershipPolicy);
//    }

    @Test
    @DisplayName("Service can be constructed (smoke test verifying dependency wiring)")
    void constructed() {
        assertThat(service).isNotNull();
    }
}