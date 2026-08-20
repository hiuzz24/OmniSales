package fu.osms.sync.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.sync.lazada.service.LazadaAuthorizedApiClient;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.tiktok.TikTokAuthorizedApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MarketplaceWarehouseConsistencyServiceImpl Tests")
class MarketplaceWarehouseConsistencyServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ShopifyApiClient shopifyApiClient;
    @Mock private LazadaAuthorizedApiClient lazadaApiClient;
    @Mock private TikTokAuthorizedApiClient tikTokApiClient;

    private MarketplaceWarehouseConsistencyServiceImpl service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new MarketplaceWarehouseConsistencyServiceImpl(
                channelRepository, credentialRepository, warehouseRepository,
                shopifyApiClient, lazadaApiClient, tikTokApiClient, objectMapper);
    }

    private Channel channel(UUID id, PlatformType platform, Map<String, Object> metadata) {
        return Channel.builder()
                .id(id)
                .platform(platform)
                .displayName("Ch-" + id)
                .syncEnabled(true)
                .metadata(metadata == null ? null : new HashMap<>(metadata))
                .build();
    }

    private ChannelCredential credential(String token) {
        return ChannelCredential.builder().id(UUID.randomUUID()).accessToken(token).connectionState("CONNECTED").build();
    }

    @Test
    @DisplayName("resolveAndValidatePrimaryWarehouse: returns shared warehouse for unsupported platform")
    void resolveAndValidate_unsupportedPlatform() {
        Warehouse shared = Warehouse.builder().id(UUID.randomUUID()).name("Kho mặc định đa sàn").build();
        when(warehouseRepository.findFirstByNameAndDeletedAtIsNullOrderByIdAsc("Kho mặc định đa sàn"))
                .thenReturn(Optional.empty());
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

        Warehouse result = service.resolveAndValidatePrimaryWarehouse(channel(UUID.randomUUID(), PlatformType.MANUAL, null));

        assertThat(result.getName()).isEqualTo("Kho mặc định đa sàn");
    }

    @Test
    @DisplayName("resolveAndValidatePrimaryWarehouse: returns shared warehouse when channel is null")
    void resolveAndValidate_nullChannel() {
        when(warehouseRepository.findFirstByNameAndDeletedAtIsNullOrderByIdAsc("Kho mặc định đa sàn"))
                .thenReturn(Optional.empty());
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

        Warehouse result = service.resolveAndValidatePrimaryWarehouse(null);

        assertThat(result.getName()).isEqualTo("Kho mặc định đa sàn");
    }

    @Test
    @DisplayName("validateConnectedPrimaryWarehouses: no-op when fewer than 2 connected marketplaces")
    void validate_singleMarketplace() {
        Channel lz = channel(UUID.randomUUID(), PlatformType.LAZADA, Map.of("accountId", "a"));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lz));
        when(credentialRepository.findByChannelIdAndConnectionState(lz.getId(), "CONNECTED"))
                .thenReturn(Optional.of(credential("token")));
        when(lazadaApiClient.executeGet(eq(lz.getId()), org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":[{\"name\":\"WH1\",\"warehouse_code\":\"WH1\",\"detail_address\":\"123 Main St\"}]}");

        service.validateConnectedPrimaryWarehouses();
    }

    @Test
    @DisplayName("validateConnectedPrimaryWarehouses: throws when connected marketplaces report mismatched addresses")
    void validate_mismatchedAddresses() {
        UUID lzId = UUID.randomUUID();
        UUID shId = UUID.randomUUID();
        Channel lz = channel(lzId, PlatformType.LAZADA, Map.of("accountId", "a"));
        Channel sh = channel(shId, PlatformType.SHOPIFY, Map.of("shopDomain", "shop.myshopify.com"));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lz, sh));
        when(credentialRepository.findByChannelIdAndConnectionState(org.mockito.ArgumentMatchers.any(UUID.class), eq("CONNECTED")))
                .thenReturn(Optional.of(credential("token")));
        when(lazadaApiClient.executeGet(eq(lz.getId()), org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":[{\"name\":\"WH1\",\"warehouse_code\":\"WH1\",\"detail_address\":\"123 Main St\"}]}");
        when(shopifyApiClient.executeGraphQl(eq("shop.myshopify.com"), eq("token"), org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn(Map.of("data", Map.of("locations", Map.of("nodes", List.of(
                        Map.of("isPrimary", true, "isActive", true, "address", Map.of("address1", "456 Different St")))))));

        assertThatThrownBy(() -> service.validateConnectedPrimaryWarehouses())
                .isInstanceOf(AppException.class)
                .hasMessageContaining("chưa đồng nhất");
    }

    @Test
    @DisplayName("validateConnectedPrimaryWarehouses: passes when two marketplaces report same address")
    void validate_sameAddress() {
        UUID lzId = UUID.randomUUID();
        UUID shId = UUID.randomUUID();
        Channel lz = channel(lzId, PlatformType.LAZADA, Map.of("accountId", "a"));
        Channel sh = channel(shId, PlatformType.SHOPIFY, Map.of("shopDomain", "shop.myshopify.com"));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lz, sh));
        when(credentialRepository.findByChannelIdAndConnectionState(org.mockito.ArgumentMatchers.any(UUID.class), eq("CONNECTED")))
                .thenReturn(Optional.of(credential("token")));
        when(lazadaApiClient.executeGet(eq(lzId), org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":[{\"name\":\"WH1\",\"warehouse_code\":\"WH1\",\"detail_address\":\"123 Common St\"}]}");
        when(shopifyApiClient.executeGraphQl(eq("shop.myshopify.com"), eq("token"), org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn(Map.of("data", Map.of("locations", Map.of("nodes", List.of(
                        Map.of("isPrimary", true, "isActive", true, "address", Map.of("address1", "123 Common St")))))));

        service.validateConnectedPrimaryWarehouses();
    }

    @Test
    @DisplayName("validateConnectedPrimaryWarehouses: passes when addresses differ only by formatting (glued house number vs spaced)")
    void validate_sameAddressDifferentFormatting() {
        UUID lzId = UUID.randomUUID();
        UUID shId = UUID.randomUUID();
        Channel lz = channel(lzId, PlatformType.LAZADA, Map.of("accountId", "a"));
        Channel sh = channel(shId, PlatformType.SHOPIFY, Map.of("shopDomain", "shop.myshopify.com"));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(lz, sh));
        when(credentialRepository.findByChannelIdAndConnectionState(org.mockito.ArgumentMatchers.any(UUID.class), eq("CONNECTED")))
                .thenReturn(Optional.of(credential("token")));
        when(lazadaApiClient.executeGet(eq(lz.getId()), org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn("{\"code\":\"0\",\"data\":[{\"name\":\"WH1\",\"warehouse_code\":\"WH1\","
                        + "\"detail_address\":\"243Khuất Duy Tiến, 243, Phường Cầu Giấy (mới), Hà Nội (mới), Vietnam\"}]}");
        when(shopifyApiClient.executeGraphQl(eq("shop.myshopify.com"), eq("token"), org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn(Map.of("data", Map.of("locations", Map.of("nodes", List.of(
                        Map.of("isPrimary", true, "isActive", true, "address", Map.of("address1", "243 Khuất Duy Tiến")))))));

        service.validateConnectedPrimaryWarehouses();
    }

    @Test
    @DisplayName("resolveMasterWarehouse: reuses existing shared warehouse when one already exists")
    void resolveMaster_existing() {
        Warehouse existing = Warehouse.builder().id(UUID.randomUUID()).name("Kho mặc định đa sàn").isActive(true).build();
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of());
        when(warehouseRepository.findByDeletedAtIsNull()).thenReturn(List.of(existing));

        Warehouse result = service.resolveMasterWarehouse();

        assertThat(result.getId()).isEqualTo(existing.getId());
        assertThat(result.getName()).isEqualTo("Kho mặc định đa sàn");
    }

    @Test
    @DisplayName("resolveAndValidatePrimaryWarehouse: updates local warehouse address from platform when they differ")
    void resolveAndValidate_updatesAddressFromPlatform() {
        UUID warehouseId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Warehouse localWarehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Kho mac dinh da san")
                .address("123 Cu Si, Quan 1, Ho Chi Minh")
                .isActive(true)
                .build();
        Channel sh = channel(channelId, PlatformType.SHOPIFY,
                Map.of("shopDomain", "shop.myshopify.com", "defaultWarehouseId", warehouseId.toString()));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(sh));
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential("token")));
        when(shopifyApiClient.executeGraphQl(eq("shop.myshopify.com"), eq("token"),
                org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn(Map.of("data", Map.of("locations", Map.of("nodes", List.of(
                        Map.of("isPrimary", true, "isActive", true,
                                "address", Map.of("formatted", List.of("456 New Street, District 3, Ho Chi Minh City"))))))));
        when(warehouseRepository.findByDeletedAtIsNull()).thenReturn(List.of(localWarehouse));
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> inv.getArgument(0));

        Warehouse result = service.resolveAndValidatePrimaryWarehouse(sh);

        assertThat(result.getAddress()).isNotEqualTo("123 Cu Si, Quan 1, Ho Chi Minh");
    }

    @Test
    @DisplayName("resolveAndValidatePrimaryWarehouse: keeps local warehouse address when already similar to platform")
    void resolveAndValidate_keepsAddressWhenSimilar() {
        UUID warehouseId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        Warehouse localWarehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Kho mac dinh da san")
                .address("123 Khuat Duy Tien, Ha Noi")
                .isActive(true)
                .build();
        Channel sh = channel(channelId, PlatformType.SHOPIFY,
                Map.of("shopDomain", "shop.myshopify.com", "defaultWarehouseId", warehouseId.toString()));
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(sh));
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.of(credential("token")));
        when(shopifyApiClient.executeGraphQl(eq("shop.myshopify.com"), eq("token"),
                org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn(Map.of("data", Map.of("locations", Map.of("nodes", List.of(
                        Map.of("isPrimary", true, "isActive", true,
                                "address", Map.of("address1", "123 Khuat Duy Tien, Cau Giay, Ha Noi")))))));
        when(warehouseRepository.findByDeletedAtIsNull()).thenReturn(List.of(localWarehouse));

        Warehouse result = service.resolveAndValidatePrimaryWarehouse(sh);

        assertThat(result.getAddress()).isEqualTo("123 Khuat Duy Tien, Ha Noi");
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}