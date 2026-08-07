package fu.osms.catalog.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.dto.request.ChannelConfigRequest;
import fu.osms.catalog.dto.response.ChannelProductConfigResponse;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.service.PlatformLookupService;
import fu.osms.catalog.service.TikTokProductTitleResolver;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductChannelConfigServiceImpl Tests")
class ProductChannelConfigServiceImplTest {

    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private PlatformLookupServiceFactory lookupServiceFactory;
    @Mock private PlatformLookupService platformLookupService;
    @Mock private TikTokProductTitleResolver tikTokProductTitleResolver;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProductChannelConfigServiceImpl service;
    private UUID productId;
    private UUID channelId;

    @BeforeEach
    void setUp() {
        service = new ProductChannelConfigServiceImpl(
                channelProductRepository,
                productVariantRepository,
                lookupServiceFactory,
                objectMapper,
                tikTokProductTitleResolver);
        productId = UUID.randomUUID();
        channelId = UUID.randomUUID();
    }

    private Channel channel(PlatformType platform) {
        return Channel.builder()
                .id(channelId)
                .platform(platform)
                .displayName("Test " + platform.name())
                .build();
    }

    private Product productWithShippingAttrs() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("packageWidthCm", "10");
        attrs.put("packageHeightCm", "20");
        attrs.put("packageLengthCm", "30");
        return Product.builder()
                .id(productId)
                .name("Espresso Beans")
                .description("Premium dark roast")
                .weightGrams(500)
                .attributes(attrs)
                .build();
    }

    private ChannelProduct channelProduct(PlatformType platform, Map<String, Object> config) {
        Channel ch = channel(platform);
        Product pr = productWithShippingAttrs();
        Map<String, Object> metadata = new HashMap<>();
        if (config != null) metadata.put("platformConfig", config);
        return ChannelProduct.builder()
                .channel(ch)
                .product(pr)
                .mappingState("ACTIVE")
                .metadata(metadata)
                .build();
    }

    @Test
    @DisplayName("applyInitialConfig: null request marks SHOPIFY ready and clears any configurationError")
    void applyInitialConfig_shopifyWithoutConfig_marksReady() {
        ChannelProduct cp = channelProduct(PlatformType.SHOPIFY, null);

        service.applyInitialConfig(cp, null);

        Map<String, Object> config = (Map<String, Object>) cp.getMetadata().get("platformConfig");
        assertThat(config).containsEntry("readyToSync", true);
        assertThat(config).doesNotContainKey("configurationError");
    }

    @Test
    @DisplayName("applyInitialConfig: null request on LAZADA marks NOT ready with a 'Missing platform category' error")
    void applyInitialConfig_lazadaWithoutConfig_marksNotReady() {
        ChannelProduct cp = channelProduct(PlatformType.LAZADA, null);

        service.applyInitialConfig(cp, null);

        Map<String, Object> config = (Map<String, Object>) cp.getMetadata().get("platformConfig");
        assertThat(config).containsEntry("readyToSync", false);
        assertThat(config.get("configurationError")).asString().contains("Missing platform category");
    }

    @Test
    @DisplayName("applyInitialConfig: null request on TIKTOK marks NOT ready with a 'Missing platform category' error")
    void applyInitialConfig_tiktokWithoutConfig_marksNotReady() {
        ChannelProduct cp = channelProduct(PlatformType.TIKTOK, null);

        service.applyInitialConfig(cp, null);

        Map<String, Object> config = (Map<String, Object>) cp.getMetadata().get("platformConfig");
        assertThat(config).containsEntry("readyToSync", false);
        assertThat(config.get("configurationError")).asString().contains("Missing platform category");
    }

    @Test
    @DisplayName("getConfig throws AppException when the product/channel mapping is missing or ARCHIVED")
    void getConfig_throwsWhenMappingMissing() {
        when(channelProductRepository.findByProductIdAndChannelId(productId, channelId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConfig(productId, channelId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Active product channel mapping");
    }

    @Test
    @DisplayName("getConfig returns the channel product config response when mapping is ACTIVE (SHOPIFY)")
    void getConfig_returnsResponse() {
        // Use SHOPIFY so shipping fields aren't required.
        Map<String, Object> config = new HashMap<>();
        config.put("categoryId", "cat-12");
        config.put("categoryName", "Coffee Beans");
        config.put("readyToSync", true);
        ChannelProduct cp = channelProduct(PlatformType.SHOPIFY, config);
        when(channelProductRepository.findByProductIdAndChannelId(productId, channelId))
                .thenReturn(Optional.of(cp));

        ChannelProductConfigResponse response = service.getConfig(productId, channelId);

        assertThat(response.getCategoryId()).isEqualTo("cat-12");
        assertThat(response.getCategoryName()).isEqualTo("Coffee Beans");
        assertThat(response.getReadyToSync()).isTrue();
        assertThat(response.getPlatform()).isEqualTo("SHOPIFY");
    }

    @Test
    @DisplayName("isReady returns true for SHOPIFY mappings regardless of config payload")
    void isReady_shopifyAlwaysReady() {
        ChannelProduct cp = channelProduct(PlatformType.SHOPIFY, null);

        assertThat(service.isReady(cp)).isTrue();
    }

    @Test
    @DisplayName("isReady returns true when readyToSync=true and there is no platform validation error (SHOPIFY)")
    void isReady_returnsTrueWhenConfigClean() {
        Map<String, Object> config = new HashMap<>();
        config.put("readyToSync", true);
        ChannelProduct cp = channelProduct(PlatformType.SHOPIFY, config);

        assertThat(service.isReady(cp)).isTrue();
    }

    @Test
    @DisplayName("isReady returns false when readyToSync=false (LAZADA + valid shipping)")
    void isReady_returnsFalseWhenNotReady() {
        Map<String, Object> config = new HashMap<>();
        config.put("readyToSync", false);
        config.put("configurationError", "Missing category");
        ChannelProduct cp = channelProduct(PlatformType.LAZADA, config);

        assertThat(service.isReady(cp)).isFalse();
    }

    @Test
    @DisplayName("isReady returns false when channel product is null or has no channel")
    void isReady_returnsFalseForNullInputs() {
        assertThat(service.isReady(null)).isFalse();
        assertThat(service.isReady(ChannelProduct.builder().build())).isFalse();
    }

    @Test
    @DisplayName("configurationError returns the persisted error verbatim, when present")
    void configurationError_returnsPersistedError() {
        Map<String, Object> config = new HashMap<>();
        config.put("configurationError", "Missing Lazada brand");
        ChannelProduct cp = channelProduct(PlatformType.SHOPIFY, config);

        assertThat(service.configurationError(cp)).isEqualTo("Missing Lazada brand");
    }

    @Test
    @DisplayName("configurationError returns null when readyToSync=true (SHOPIFY)")
    void configurationError_returnsNullWhenReady() {
        Map<String, Object> config = new HashMap<>();
        config.put("readyToSync", true);
        ChannelProduct cp = channelProduct(PlatformType.SHOPIFY, config);

        assertThat(service.configurationError(cp)).isNull();
    }

    @Test
    @DisplayName("updateConfig throws AppException when request body is null (before any repository call)")
    void updateConfig_throwsForNullRequest() {
        assertThatThrownBy(() -> service.updateConfig(productId, channelId, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Channel configuration");
    }

    @Test
    @DisplayName("updateConfig throws AppException when request.channelId is set and differs from the path id (before any repository call)")
    void updateConfig_throwsWhenRequestChannelMismatches() {
        ChannelConfigRequest req = new ChannelConfigRequest();
        req.setChannelId(UUID.randomUUID());

        assertThatThrownBy(() -> service.updateConfig(productId, channelId, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("does not match path");
    }

    @Test
    @DisplayName("updateConfig: SHOPIFY always marks readyToSync=true, clears the error, and persists PENDING sync status")
    void updateConfig_shopifyForcesReady() {
        Map<String, Object> config = new HashMap<>();
        config.put("readyToSync", false);
        config.put("configurationError", "prior error");
        ChannelProduct cp = channelProduct(PlatformType.SHOPIFY, config);
        when(channelProductRepository.findByProductIdAndChannelId(productId, channelId))
                .thenReturn(Optional.of(cp));

        ChannelConfigRequest req = new ChannelConfigRequest();
        req.setChannelId(channelId);

        ChannelProductConfigResponse response = service.updateConfig(productId, channelId, req);

        assertThat(response.getReadyToSync()).isTrue();
        assertThat(response.getConfigurationError()).isNull();
        assertThat(cp.getSyncStatus()).isEqualTo(SyncStatus.PENDING);
    }
}
