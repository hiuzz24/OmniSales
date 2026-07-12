package fu.osms.sync.lazada.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import fu.osms.common.exception.TokenExpiredException;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.lazada.service.LazadaImageService;
import fu.osms.sync.lazada.service.LazadaPayloadBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LazadaSyncServiceImplTest {

    @Mock private LazadaApiClient lazadaApiClient;
    @Mock private LazadaImageService lazadaImageService;
    @Mock private LazadaPayloadBuilder lazadaPayloadBuilder;
    @Mock private ChannelCredentialRepository channelCredentialRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LazadaSyncServiceImpl service;

    private Channel channel;
    private ChannelCredential credential;
    private ChannelProduct channelProduct;
    private Product product;
    private ProductVariant variant;

    @BeforeEach
    void setUp() {
        service = new LazadaSyncServiceImpl(
                lazadaApiClient, lazadaImageService, lazadaPayloadBuilder,
                channelCredentialRepository, channelProductRepository,
                channelProductVariantRepository, objectMapper);

        channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Lazada VN")
                .build();

        credential = ChannelCredential.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .accessToken("access-tok")
                .connectionState("CONNECTED")
                .tokenExpiresAt(OffsetDateTime.now().plusHours(1))
                .build();

        product = Product.builder()
                .id(UUID.randomUUID())
                .name("Test Product")
                .build();

        variant = ProductVariant.builder()
                .id(UUID.randomUUID())
                .product(product)
                .sku("SKU-001")
                .name("Default variant")
                .isActive(true)
                .build();

        channelProduct = ChannelProduct.builder()
                .id(UUID.randomUUID())
                .channel(channel)
                .product(product)
                .externalProductId(null)
                .syncStatus(SyncStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("syncProduct — happy new product: calls /product/create, stores externalProductId, sets SYNCED")
    void syncProduct_new_happy() throws Exception {
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(lazadaImageService.migrateImages(any(), anyString(), any())).thenReturn(List.of("https://img.laz/1"));
        when(lazadaPayloadBuilder.buildPayload(any(), any(), any(), any(), eq(true))).thenReturn("<Request>payload</Request>");
        when(lazadaApiClient.executePost(eq("/product/create"), any(), eq("access-tok"), any()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"item_id\":\"LP-12345\",\"sku_list\":[{\"seller_sku\":\"SKU-001\",\"sku_id\":\"SKU-ID-1\"}]}}");
        when(channelProductVariantRepository.findByChannelProductIdAndVariantId(channelProduct.getId(), variant.getId()))
                .thenReturn(Optional.empty());
        when(channelProductVariantRepository.save(any(ChannelProductVariant.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct);

        assertThat(ok).isTrue();
        assertThat(channelProduct.getExternalProductId()).isEqualTo("LP-12345");
        assertThat(channelProduct.getSyncStatus()).isEqualTo(SyncStatus.SYNCED);
        assertThat(channelProduct.getLastSyncError()).isNull();
        verify(channelProductRepository).save(channelProduct);
        verify(channelProductVariantRepository).save(any(ChannelProductVariant.class));
    }

    @Test
    @DisplayName("syncProduct — update existing product: calls /product/update with resolved SKU map")
    void syncProduct_update_happy() throws Exception {
        channelProduct.setExternalProductId("LP-12345");
        ChannelProductVariant mapping = ChannelProductVariant.builder()
                .id(UUID.randomUUID())
                .channelProduct(channelProduct)
                .variant(variant)
                .externalVariantId("SKU-ID-1")
                .build();

        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(lazadaImageService.migrateImages(any(), anyString(), any())).thenReturn(List.of());
        when(lazadaPayloadBuilder.buildPayload(any(), any(), any(), eq(Map.of("SKU-001", "SKU-ID-1")), eq(false)))
                .thenReturn("<Request>update</Request>");
        when(lazadaApiClient.executePost(eq("/product/update"), any(), eq("access-tok"), any()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"item_id\":\"LP-12345\"}}");
        when(channelProductVariantRepository.findByChannelProductId(channelProduct.getId()))
                .thenReturn(List.of(mapping));
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct);

        assertThat(ok).isTrue();
        verify(lazadaApiClient).executePost(eq("/product/update"), any(), any(), any());
    }

    @Test
    @DisplayName("syncProduct — missing credential throws IllegalStateException (wrapped in RuntimeException)")
    void syncProduct_noCredential() {
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access token is missing");
    }

    @Test
    @DisplayName("syncProduct — blank access token throws IllegalStateException")
    void syncProduct_blankToken() {
        credential.setAccessToken("   ");
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Access token is missing");
    }

    @Test
    @DisplayName("syncProduct — Lazada API error → RuntimeException with detail message")
    void syncProduct_apiError() {
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(lazadaImageService.migrateImages(any(), anyString(), any())).thenReturn(List.of());
        when(lazadaPayloadBuilder.buildPayload(any(), any(), any(), any(), eq(true))).thenReturn("<Request/>");
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenReturn("{\"code\":\"500\",\"message\":\"Invalid payload\",\"detail\":[{\"field\":\"price\",\"message\":\"required\"}]}");
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid payload")
                .hasMessageContaining("price");

        assertThat(channelProduct.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(channelProduct.getLastSyncError()).contains("Invalid payload");
    }

    @Test
    @DisplayName("syncProduct — TokenExpiredException bubbles up (no failed status set)")
    void syncProduct_tokenExpiredBubbles() {
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(lazadaImageService.migrateImages(any(), anyString(), any())).thenReturn(List.of());
        when(lazadaPayloadBuilder.buildPayload(any(), any(), any(), any(), eq(true))).thenReturn("<Request/>");
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenThrow(new TokenExpiredException("expired"));

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(TokenExpiredException.class);
    }

    @Test
    @DisplayName("syncProduct — new product with missing item_id in response throws")
    void syncProduct_missingItemId() {
        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(lazadaImageService.migrateImages(any(), anyString(), any())).thenReturn(List.of());
        when(lazadaPayloadBuilder.buildPayload(any(), any(), any(), any(), eq(true))).thenReturn("<Request/>");
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":{}}");

        assertThatThrownBy(() -> service.syncProduct(product, List.of(variant), List.of(), channel, channelProduct))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Missing item_id");
    }

    @Test
    @DisplayName("syncProduct — happy with image migration in the middle")
    void syncProduct_withImageMigration() throws Exception {
        ProductImage img = ProductImage.builder().id(UUID.randomUUID()).url("https://cdn/x.jpg").build();

        when(channelCredentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential));
        when(lazadaImageService.migrateImages(List.of(img), "access-tok", credential.getTokenExpiresAt().toEpochSecond()))
                .thenReturn(List.of("https://laz-img/1"));
        when(lazadaPayloadBuilder.buildPayload(any(), any(), eq(List.of("https://laz-img/1")), any(), eq(true)))
                .thenReturn("<Request/>");
        when(lazadaApiClient.executePost(any(), any(), any(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":{\"item_id\":\"NEW-1\"}}");
        when(channelProductRepository.save(any(ChannelProduct.class))).thenAnswer(i -> i.getArgument(0));

        boolean ok = service.syncProduct(product, List.of(variant), List.of(img), channel, channelProduct);

        assertThat(ok).isTrue();
        verify(lazadaImageService).migrateImages(List.of(img), "access-tok", credential.getTokenExpiresAt().toEpochSecond());
    }
}
