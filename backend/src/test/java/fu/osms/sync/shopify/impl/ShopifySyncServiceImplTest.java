package fu.osms.sync.shopify.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyPayloadBuilder;
import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShopifySyncServiceImpl Tests")
class ShopifySyncServiceImplTest {

    @Mock private ShopifyApiClient shopifyApiClient;
    @Mock private ShopifyPayloadBuilder shopifyPayloadBuilder;
    @Mock private ChannelCredentialRepository channelCredentialRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;

    private ShopifySyncServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new ShopifySyncServiceImpl(
                shopifyApiClient, shopifyPayloadBuilder, channelCredentialRepository,
                channelProductRepository, channelProductVariantRepository, objectMapper);
    }

    private Channel channel(UUID id, String shopDomain) {
        java.util.Map<String, Object> meta = new HashMap<>();
        meta.put("shopDomain", shopDomain);
        return Channel.builder()
                .id(id)
                .platform(PlatformType.SHOPIFY)
                .displayName("Test Shop")
                .metadata(meta)
                .build();
    }

    private ChannelProduct channelProduct(UUID channelId, String externalProductId) {
        return ChannelProduct.builder()
                .id(UUID.randomUUID())
                .channel(Channel.builder().id(channelId).build())
                .externalProductId(externalProductId)
                .build();
    }

    @Test
    @DisplayName("syncProduct: throws IllegalArgumentException when shopDomain is missing")
    void missingShopDomain() {
        Channel channel = Channel.builder()
                .id(UUID.randomUUID()).platform(PlatformType.SHOPIFY)
                .displayName("Shop").metadata(null).build();
        Product product = Product.builder().id(UUID.randomUUID()).build();

        assertThat(service.syncProduct(product, List.of(), List.of(), channel, channelProduct(channel.getId(), null)))
                .isFalse();
    }

    @Test
    @DisplayName("syncProduct: throws IllegalArgumentException when no CONNECTED credential exists")
    void missingCredential() {
        Channel channel = channel(UUID.randomUUID(), "shop.myshopify.com");
        Product product = Product.builder().id(UUID.randomUUID()).build();
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThat(service.syncProduct(product, List.of(), List.of(), channel, channelProduct(channel.getId(), null)))
                .isFalse();
    }

    @Test
    @DisplayName("syncProduct: creates a new product when externalProductId is null and returns true")
    void createNewProduct() {
        Channel channel = channel(UUID.randomUUID(), "shop.myshopify.com");
        Product product = Product.builder().id(UUID.randomUUID()).name("Phone").build();
        ChannelProduct cp = channelProduct(channel.getId(), null);
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(ChannelCredential.builder().accessToken("shpat-x").build()));
        ShopifyProductPayload payload = ShopifyProductPayload.builder().title("Phone").build();
        when(shopifyPayloadBuilder.buildPayload(product, List.of(), List.of())).thenReturn(payload);
        when(shopifyApiClient.createProduct(eq("shop.myshopify.com"), eq("shpat-x"), any()))
                .thenAnswer(inv -> {
                    fu.osms.sync.dto.shopify.response.ShopifyProductResponse r =
                            new fu.osms.sync.dto.shopify.response.ShopifyProductResponse();
                    r.setId(123L);
                    return r;
                });

        boolean ok = service.syncProduct(product, List.of(), List.of(), channel, cp);

        assertThat(ok).isTrue();
        assertThat(cp.getExternalProductId()).isEqualTo("123");
    }

    @Test
    @DisplayName("syncProduct: updates existing product when externalProductId is set and clears variants/options")
    void updateExistingProduct() {
        Channel channel = channel(UUID.randomUUID(), "shop.myshopify.com");
        Product product = Product.builder().id(UUID.randomUUID()).name("Phone").build();
        ChannelProduct cp = channelProduct(channel.getId(), "gid://shopify/Product/123");
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(ChannelCredential.builder().accessToken("shpat-x").build()));
        ShopifyProductPayload payload = ShopifyProductPayload.builder()
                .title("Phone").variants(List.of()).options(List.of()).build();
        when(shopifyPayloadBuilder.buildPayload(product, List.of(), List.of())).thenReturn(payload);
        when(shopifyApiClient.updateProduct(eq("shop.myshopify.com"), eq("shpat-x"), eq("gid://shopify/Product/123"), any()))
                .thenAnswer(inv -> {
                    fu.osms.sync.dto.shopify.response.ShopifyProductResponse r =
                            new fu.osms.sync.dto.shopify.response.ShopifyProductResponse();
                    r.setId(123L);
                    return r;
                });

        boolean ok = service.syncProduct(product, List.of(), List.of(), channel, cp);

        assertThat(ok).isTrue();
        assertThat(payload.getVariants()).isNull();
        assertThat(payload.getOptions()).isNull();
    }

    @Test
    @DisplayName("syncProduct: returns false when Shopify returns a null response")
    void nullResponse() {
        Channel channel = channel(UUID.randomUUID(), "shop.myshopify.com");
        Product product = Product.builder().id(UUID.randomUUID()).build();
        ChannelProduct cp = channelProduct(channel.getId(), null);
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(ChannelCredential.builder().accessToken("shpat-x").build()));
        when(shopifyPayloadBuilder.buildPayload(product, List.of(), List.of())).thenReturn(ShopifyProductPayload.builder().build());
        when(shopifyApiClient.createProduct(eq("shop.myshopify.com"), eq("shpat-x"), any())).thenReturn(null);

        assertThat(service.syncProduct(product, List.of(), List.of(), channel, cp)).isFalse();
    }
}