package fu.osms.sync.tiktok.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.catalog.service.ProductChannelConfigService;
import fu.osms.catalog.service.impl.PlatformLookupServiceFactory;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import fu.osms.sync.tiktok.TikTokProductPayloadBuilder;
import fu.osms.catalog.service.TikTokProductTitleResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokSyncServiceImpl Smoke Tests")
class TikTokSyncServiceImplTest {

    @Mock private TikTokAuthorizedApiClient tikTokApiClient;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private ProductChannelConfigService productChannelConfigService;
    @Mock private PlatformLookupServiceFactory lookupServiceFactory;
    @Mock private TikTokProductPayloadBuilder payloadBuilder;
    @Mock private TikTokProductTitleResolver titleResolver;

    private TikTokSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TikTokSyncServiceImpl(
                tikTokApiClient, channelProductRepository, channelProductVariantRepository,
                productChannelConfigService, lookupServiceFactory,
                payloadBuilder, titleResolver, new ObjectMapper());
    }

    @Test
    @DisplayName("Service can be constructed (smoke test verifying dependency wiring)")
    void constructed() {
        assertThat(service).isNotNull();
    }
}