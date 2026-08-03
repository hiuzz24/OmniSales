package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.sync.service.MarketplaceStockQuantityResolver;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.inventory.TikTokInventoryGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokInventoryUpdateServiceImpl Smoke Tests")
class TikTokInventoryUpdateServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelProductVariantRepository mappingRepository;
    @Mock private MarketplaceStockQuantityResolver quantityResolver;
    @Mock private TikTokInventoryGateway inventoryGateway;
    @Mock private TikTokAuthorizedApiClient tikTokApiClient;

    private TikTokInventoryUpdateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TikTokInventoryUpdateServiceImpl(
                channelRepository, mappingRepository, quantityResolver,
                inventoryGateway, tikTokApiClient, new ObjectMapper());
    }

    @Test
    @DisplayName("Service can be constructed (smoke test verifying dependency wiring)")
    void constructed() {
        assertThat(service).isNotNull();
    }
}