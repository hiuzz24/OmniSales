package fu.osms.sync.shopify.impl;

import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.StockReceiveRepository;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.inventory.ShopifyInventoryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifyInventoryUpdateServiceImpl Smoke Tests")
class ShopifyInventoryUpdateServiceImplTest {

    @Mock private ShopifyApiClient shopifyApiClient;
    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private StockReceiveRepository stockReceiveRepository;
    @Mock private InventoryIssueRepository inventoryIssueRepository;
    @Mock private MarketplaceStockQuantityResolver marketplaceStockQuantityResolver;
    @Mock private ShopifyInventoryGateway inventoryGateway;

    private ShopifyInventoryUpdateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ShopifyInventoryUpdateServiceImpl(
                shopifyApiClient, channelRepository, credentialRepository,
                channelProductVariantRepository, inventoryItemRepository,
                stockReceiveRepository, inventoryIssueRepository,
                marketplaceStockQuantityResolver, inventoryGateway);
    }

    @Test
    @DisplayName("Service can be constructed (smoke test verifying dependency wiring)")
    void constructed() {
        assertThat(service).isNotNull();
    }
}