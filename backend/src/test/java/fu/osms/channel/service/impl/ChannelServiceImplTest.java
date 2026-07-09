package fu.osms.channel.service.impl;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelConnectionLog;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProduct;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.mapper.ChannelProductMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelServiceImpl - Unit Tests")
class ChannelServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;
    @Mock private ChannelMapper channelMapper;
    @Mock private ChannelProductMapper channelProductMapper;
    @Mock private ChannelConnectionLogService channelConnectionLogService;
    @Mock private ShopifyWebhookSubscriptionService shopifyWebhookSubscriptionService;

    @InjectMocks
    private ChannelServiceImpl channelService;

    private Channel sample;

    @BeforeEach
    void setUp() {
        sample = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Lazada-shop-test")
                .status("CONNECTED")
                .syncEnabled(true)
                .commissionRate(BigDecimal.ZERO)
                .metadata(new HashMap<>())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("create - persists channel and credential with CONNECTED status")
    void create_succeeds() {
        ChannelRequest req = ChannelRequest.builder()
                .platform(PlatformType.MANUAL)
                .displayName("Manual channel")
                .region("VN")
                .commissionRate(BigDecimal.ZERO)
                .syncEnabled(true)
                .build();

        when(channelRepository.existsByPlatformAndDisplayName(req.getPlatform(), req.getDisplayName()))
                .thenReturn(false);
        when(channelMapper.toEntity(req)).thenReturn(
                Channel.builder().platform(req.getPlatform()).displayName(req.getDisplayName()).build()
        );
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(channelMapper.toResponse(any(Channel.class))).thenAnswer(inv ->
                ChannelResponse.builder()
                        .id(((Channel) inv.getArgument(0)).getId())
                        .platform(((Channel) inv.getArgument(0)).getPlatform())
                        .displayName(((Channel) inv.getArgument(0)).getDisplayName())
                        .status("CONNECTED")
                        .build()
        );

        ChannelResponse res = channelService.create(req);

        assertThat(res.getStatus()).isEqualTo("CONNECTED");
        verify(credentialRepository).save(any(ChannelCredential.class));
    }

    @Test
    @DisplayName("create - throws AppException when (platform, displayName) already exists")
    void create_duplicateThrows() {
        ChannelRequest req = ChannelRequest.builder()
                .platform(PlatformType.LAZADA)
                .displayName("dup-shop")
                .build();
        when(channelRepository.existsByPlatformAndDisplayName(req.getPlatform(), req.getDisplayName()))
                .thenReturn(true);

        assertThatThrownBy(() -> channelService.create(req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.CHANNEL_ALREADY_EXISTS.getMessage());

        verify(channelRepository, never()).save(any());
    }

    @Test
    @DisplayName("getById - returns enriched channel with stats metadata")
    void getById_succeeds() {
        when(channelRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(channelProductRepository.countByChannelIdAndMappingState(sample.getId(), "ACTIVE")).thenReturn(7L);
        when(channelProductVariantRepository.countActiveByChannelId(sample.getId())).thenReturn(15L);
        // mapper returns the entity itself wrapped in a response - metadata is what service just set on entity
        when(channelMapper.toResponse(any(Channel.class))).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            return ChannelResponse.builder()
                    .id(c.getId())
                    .platform(c.getPlatform())
                    .displayName(c.getDisplayName())
                    .metadata(c.getMetadata())
                    .build();
        });

        ChannelResponse res = channelService.getById(sample.getId());

        assertThat(res.getMetadata()).containsEntry("productCount", 7L);
        assertThat(res.getMetadata()).containsEntry("skuVariantCount", 15L);
    }

    @Test
    @DisplayName("getById - throws AppException when channel missing or soft-deleted")
    void getById_notFoundThrows() {
        sample.setDeletedAt(OffsetDateTime.now());
        when(channelRepository.findById(sample.getId())).thenReturn(Optional.of(sample));

        assertThatThrownBy(() -> channelService.getById(sample.getId()))
                .isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("getAll - returns enriched list of non-deleted channels")
    void getAll_returnsEnrichedList() {
        when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(sample));
        when(channelProductRepository.countByChannelIdAndMappingState(sample.getId(), "ACTIVE")).thenReturn(3L);
        when(channelProductVariantRepository.countActiveByChannelId(sample.getId())).thenReturn(8L);
        when(channelMapper.toResponse(any(Channel.class))).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            return ChannelResponse.builder()
                    .id(c.getId())
                    .platform(c.getPlatform())
                    .displayName(c.getDisplayName())
                    .metadata(c.getMetadata())
                    .build();
        });

        List<ChannelResponse> result = channelService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMetadata()).containsEntry("productCount", 3L);
    }

    @Test
    @DisplayName("update - persists updated displayName, commissionRate, syncEnabled, metadata")
    void update_succeeds() {
        ChannelRequest req = ChannelRequest.builder()
                .platform(PlatformType.LAZADA)
                .displayName("renamed-shop")
                .commissionRate(new BigDecimal("12.5"))
                .syncEnabled(false)
                .metadata(Map.of("key", "value"))
                .build();
        when(channelRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(channelMapper.toResponse(sample)).thenReturn(
                ChannelResponse.builder().id(sample.getId()).displayName("renamed-shop").build()
        );

        ChannelResponse res = channelService.update(sample.getId(), req);

        assertThat(sample.getDisplayName()).isEqualTo("renamed-shop");
        assertThat(sample.getCommissionRate()).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(sample.getSyncEnabled()).isFalse();
        assertThat(sample.getMetadata()).containsEntry("key", "value");
        assertThat(res.getDisplayName()).isEqualTo("renamed-shop");
    }

    @Test
    @DisplayName("delete - soft-deletes, archives mapped products and logs DISCONNECT")
    void delete_softDeletesAndArchives() {
        when(channelRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(credentialRepository.findByChannelId(sample.getId())).thenReturn(Optional.empty());
        ChannelProduct cp = ChannelProduct.builder().channel(sample).mappingState("ACTIVE").build();
        when(channelProductRepository.findByChannelId(sample.getId())).thenReturn(List.of(cp));

        channelService.delete(sample.getId());

        assertThat(sample.getDeletedAt()).isNotNull();
        verify(channelProductRepository).saveAll(anyList());
        assertThat(cp.getMappingState()).isEqualTo("ARCHIVED");
        verify(channelConnectionLogService).logSuccess(eq(sample), eq(ChannelConnectionAction.DISCONNECT),
                any(String.class), any(Map.class));
    }

    @Test
    @DisplayName("connectShopify - strips .myshopify.com suffix and stores shopDomain")
    void connectShopify_normalizesShopDomain() {
        when(channelRepository.findActiveShopifyByShopDomain("demo-shop")).thenReturn(Optional.empty());
        when(channelRepository.findByPlatformAndDisplayName(PlatformType.SHOPIFY, "demo-shop"))
                .thenReturn(Optional.empty());
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(credentialRepository.findByChannelId(any(UUID.class))).thenReturn(Optional.empty());
        when(channelMapper.toResponse(any(Channel.class))).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            return ChannelResponse.builder().id(c.getId()).platform(c.getPlatform())
                    .displayName(c.getDisplayName()).build();
        });

        ChannelResponse res = channelService.connectShopify("demo-shop.myshopify.com", "tok-abc");

        assertThat(res.getPlatform()).isEqualTo(PlatformType.SHOPIFY);
        ArgumentCaptor<Channel> captor = ArgumentCaptor.forClass(Channel.class);
        verify(channelRepository).save(captor.capture());
        assertThat(captor.getValue().getMetadata()).containsEntry("shopDomain", "demo-shop");
        verify(credentialRepository).save(any(ChannelCredential.class));
        verify(channelConnectionLogService).logSuccess(any(Channel.class),
                eq(ChannelConnectionAction.CONNECT), any(String.class), any(Map.class));
    }

    @Test
    @DisplayName("connectLazada - creates new channel when no matching accountId; logs token expiry")
    void connectLazada_createsNewChannel() {
        when(channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA))
                .thenReturn(new ArrayList<>());
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            if (c.getId() == null) c.setId(UUID.randomUUID());
            return c;
        });
        when(credentialRepository.findByChannelId(any(UUID.class))).thenReturn(Optional.empty());
        when(channelMapper.toResponse(any(Channel.class))).thenAnswer(inv -> {
            Channel c = inv.getArgument(0);
            return ChannelResponse.builder().id(c.getId()).displayName(c.getDisplayName()).build();
        });

        ChannelResponse res = channelService.connectLazada("access-tok", "refresh-tok",
                3600, "acc-001", "Demo Lazada");

        assertThat(res.getDisplayName()).isEqualTo("Lazada-Demo Lazada");
        ArgumentCaptor<ChannelCredential> capCred = ArgumentCaptor.forClass(ChannelCredential.class);
        verify(credentialRepository).save(capCred.capture());
        assertThat(capCred.getValue().getTokenExpiresAt()).isNotNull();
        assertThat(capCred.getValue().getRefreshToken()).isEqualTo("refresh-tok");
    }

    @Test
    @DisplayName("connectLazada - matches existing channel by accountId in metadata")
    void connectLazada_reusesExistingChannelByAccountId() {
        Channel existing = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Lazada-Old")
                .metadata(new HashMap<>(Map.of("accountId", "acc-001")))
                .status("DISCONNECTED")
                .syncEnabled(false)
                .build();
        when(channelRepository.findByPlatformAndDeletedAtIsNull(PlatformType.LAZADA))
                .thenReturn(List.of(existing));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(credentialRepository.findByChannelId(existing.getId())).thenReturn(Optional.empty());
        when(channelMapper.toResponse(any(Channel.class))).thenAnswer(inv ->
                ChannelResponse.builder().id(((Channel) inv.getArgument(0)).getId()).build()
        );

        ChannelResponse res = channelService.connectLazada("access-tok", "refresh-tok",
                3600, "acc-001", "Demo");

        assertThat(res.getId()).isEqualTo(existing.getId());
        assertThat(existing.getSyncEnabled()).isFalse(); // unchanged - syncEnabled must be null OR deletedAt != null to flip to true
        verify(channelConnectionLogService, times(1)).logSuccess(eq(existing),
                eq(ChannelConnectionAction.RECONNECT), any(String.class), any(Map.class));
    }

    @Test
    @DisplayName("updateShopifyWebhookMetadata - merges webhook result into channel metadata")
    void updateShopifyWebhookMetadata_merges() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("shopDomain", "x");
        sample.setMetadata(existing);

        when(channelRepository.findById(sample.getId())).thenReturn(Optional.of(sample));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> inv.getArgument(0));

        fu.osms.sync.dto.shopify.WebhookRegistrationResult result =
                fu.osms.sync.dto.shopify.WebhookRegistrationResult.builder()
                        .status("SUCCESS")
                        .error(null)
                        .webhooks(List.of())
                        .build();

        channelService.updateShopifyWebhookMetadata(sample.getId(), result);

        assertThat(sample.getMetadata()).containsEntry("shopDomain", "x");
        assertThat(sample.getMetadata()).containsEntry("webhookRegistrationStatus", "SUCCESS");
    }
}
