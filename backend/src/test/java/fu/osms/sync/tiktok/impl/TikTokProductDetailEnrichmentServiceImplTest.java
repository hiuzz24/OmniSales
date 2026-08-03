package fu.osms.sync.tiktok.impl;

import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.catalog.repository.ProductRepository;
import fu.osms.sync.service.PlatformCatalogOwnershipPolicy;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokProductDetailEnrichmentServiceImpl Smoke Tests")
class TikTokProductDetailEnrichmentServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private TikTokAuthorizedApiClient tikTokApiClient;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private PlatformCatalogOwnershipPolicy catalogOwnershipPolicy;

    private TikTokProductDetailEnrichmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TikTokProductDetailEnrichmentServiceImpl(
                channelRepository, channelProductRepository, channelProductVariantRepository,
                productRepository, tikTokApiClient, transactionTemplate, catalogOwnershipPolicy);
    }

    @Test
    @DisplayName("Service can be constructed (smoke test verifying dependency wiring)")
    void constructed() {
        assertThat(service).isNotNull();
    }
}