package fu.osms.channel.service.impl;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionService;
import fu.osms.channel.service.ChannelMappingLifecycleService;
import fu.osms.channel.service.ChannelProductQueryService;
import fu.osms.channel.service.ChannelResponseService;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceImplTest {

        @Mock
        private ChannelRepository channelRepository;
        @Mock
        private ChannelCredentialRepository credentialRepository;
        @Mock
        private ChannelMapper channelMapper;
        @Mock
        private ChannelConnectionService connectionService;
        @Mock
        private ChannelMappingLifecycleService mappingLifecycleService;
        @Mock
        private ChannelProductQueryService productQueryService;
        @Mock
        private ChannelResponseService responseService;

        private ChannelServiceImpl service;

        @BeforeEach
        void setUp() {
                service = new ChannelServiceImpl(
                                channelRepository,
                                credentialRepository,
                                channelMapper,
                                connectionService,
                                mappingLifecycleService,
                                productQueryService,
                                responseService);
        }

        @Test
        @DisplayName("connectLazada delegates verbatim to ChannelConnectionService with all 6 args")
        void connectLazada_delegates() {
                ChannelResponse expected = ChannelResponse.builder()
                                .id(UUID.randomUUID())
                                .platform(PlatformType.LAZADA)
                                .displayName("Lazada-Demo")
                                .build();
                when(connectionService.connectLazada("access", "refresh", 3600, 7_200_000, "acc-001", "Demo"))
                                .thenReturn(expected);

                ChannelResponse actual = service.connectLazada("access", "refresh", 3600, 7_200_000, "acc-001", "Demo");

                assertThat(actual).isSameAs(expected);
                verify(connectionService).connectLazada("access", "refresh", 3600, 7_200_000, "acc-001", "Demo");
        }

        @Test
        @DisplayName("connectTikTok forwards the metadata map to ChannelConnectionService")
        void connectTikTok_delegates() {
                Map<String, Object> metadata = Map.of("region", "VN", "shopId", "S1");
                ChannelResponse expected = ChannelResponse.builder()
                                .id(UUID.randomUUID())
                                .platform(PlatformType.TIKTOK)
                                .build();
                when(connectionService.connectTikTok("access", "refresh", 3600, 7_200_000, "acc-1", "Demo", metadata))
                                .thenReturn(expected);

                ChannelResponse actual = service.connectTikTok("access", "refresh", 3600, 7_200_000, "acc-1", "Demo",
                                metadata);

                assertThat(actual).isSameAs(expected);
                verify(connectionService).connectTikTok("access", "refresh", 3600, 7_200_000, "acc-1", "Demo",
                                metadata);
        }

        @Test
        @DisplayName("connectShopify delegates")
        void connectShopify_delegates() {
                ChannelResponse expected = ChannelResponse.builder()
                                .id(UUID.randomUUID())
                                .platform(PlatformType.SHOPIFY)
                                .displayName("demo.myshopify.com")
                                .build();
                when(connectionService.connectShopify("demo.myshopify.com", "shpat-token"))
                                .thenReturn(expected);

                ChannelResponse actual = service.connectShopify("demo.myshopify.com", "shpat-token");

                assertThat(actual).isSameAs(expected);
                verify(connectionService).connectShopify("demo.myshopify.com", "shpat-token");
        }

        @Test
        @DisplayName("getById returns mapped response and enriches stats before responding")
        void getById_enrichesAndMaps() {
                UUID id = UUID.randomUUID();
                Channel channel = Channel.builder().id(id).platform(PlatformType.LAZADA).build();
                ChannelResponse mapped = ChannelResponse.builder().id(id).platform(PlatformType.LAZADA).build();
                when(channelRepository.findById(id)).thenReturn(Optional.of(channel));
                when(responseService.toResponse(channel)).thenReturn(mapped);

                ChannelResponse actual = service.getById(id);

                assertThat(actual).isSameAs(mapped);
                verify(responseService).enrichStats(channel);
                verify(responseService).toResponse(channel);
        }

        @Test
        @DisplayName("getById throws AppException(CHANNEL_NOT_FOUND) when missing or deleted")
        void getById_missing() {
                UUID id = UUID.randomUUID();
                when(channelRepository.findById(id)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> service.getById(id))
                                .isInstanceOf(AppException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.CHANNEL_NOT_FOUND);

                verify(responseService, never()).toResponse(any(Channel.class));
        }

        @Test
        @DisplayName("getById throws when the channel is soft-deleted")
        void getById_deleted() {
                UUID id = UUID.randomUUID();
                Channel deleted = Channel.builder()
                                .id(id)
                                .platform(PlatformType.LAZADA)
                                .deletedAt(OffsetDateTime.now())
                                .build();
                when(channelRepository.findById(id)).thenReturn(Optional.of(deleted));

                assertThatThrownBy(() -> service.getById(id))
                                .isInstanceOf(AppException.class)
                                .extracting("errorCode").isEqualTo(ErrorCode.CHANNEL_NOT_FOUND);
        }

        @Test
        @DisplayName("getAll enriches then maps every active channel")
        void getAll_enrichesAndMaps() {
                Channel a = Channel.builder().id(UUID.randomUUID()).platform(PlatformType.LAZADA).build();
                Channel b = Channel.builder().id(UUID.randomUUID()).platform(PlatformType.SHOPIFY).build();
                when(channelRepository.findByDeletedAtIsNull()).thenReturn(List.of(a, b));
                when(responseService.toResponse(a)).thenReturn(
                                ChannelResponse.builder().id(a.getId()).platform(PlatformType.LAZADA).build());
                when(responseService.toResponse(b)).thenReturn(
                                ChannelResponse.builder().id(b.getId()).platform(PlatformType.SHOPIFY).build());

                List<ChannelResponse> result = service.getAll();

                assertThat(result).hasSize(2);
                verify(responseService, times(2)).enrichStats(any(Channel.class));
        }

        @Test
        @DisplayName("create throws AppException(CHANNEL_ALREADY_EXISTS) when platform + displayName already exists")
        void create_alreadyExists() {
            ChannelRequest req = ChannelRequest.builder()
                    .platform(PlatformType.SHOPEE)
                    .displayName("Shopee-Existing")
                    .build();
            when(channelRepository.findByPlatformAndDisplayName(PlatformType.SHOPEE, "Shopee-Existing"))
                    .thenReturn(Optional.of(Channel.builder()
                            .id(UUID.randomUUID())
                            .platform(PlatformType.SHOPEE)
                            .displayName("Shopee-Existing")
                            .build()));

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.CHANNEL_ALREADY_EXISTS);

            verify(channelMapper, never()).toEntity(any());
            verify(channelRepository, never()).save(any(Channel.class));
        }

        @Test
        @DisplayName("create rejects OAuth-only platforms with INVALID_REQUEST")
        void create_oauthOnlyPlatformRejected() {
            ChannelRequest req = ChannelRequest.builder()
                    .platform(PlatformType.LAZADA)
                    .displayName("Lazada-Via-Create")
                    .build();

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(AppException.class)
                    .extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);

            verify(channelRepository, never()).save(any(Channel.class));
        }

        @Test
        @DisplayName("create persists channel + initial CONNECTED credential and returns response")
        void create_persists() {
            ChannelRequest req = ChannelRequest.builder()
                    .platform(PlatformType.SHOPEE)
                    .displayName("Shopee-New")
                    .metadata(Map.of("accountId", "acc-99"))
                    .commissionRate(new BigDecimal("3.50"))
                    .build();
            Channel savedChannel = Channel.builder()
                    .id(UUID.randomUUID())
                    .platform(PlatformType.SHOPEE)
                    .displayName("Shopee-New")
                    .metadata(Map.of("accountId", "acc-99"))
                    .build();
            ChannelResponse mappedResponse = ChannelResponse.builder().id(savedChannel.getId())
                    .platform(PlatformType.SHOPEE).build();

            when(channelRepository.findByPlatformAndDisplayName(PlatformType.SHOPEE, "Shopee-New"))
                    .thenReturn(Optional.empty());
            when(channelMapper.toEntity(req)).thenReturn(Channel.builder().platform(PlatformType.SHOPEE)
                    .displayName("Shopee-New").metadata(new java.util.HashMap<>()).build());
            when(channelRepository.save(any(Channel.class))).thenReturn(savedChannel);
            when(responseService.toResponse(any(Channel.class))).thenReturn(mappedResponse);

            ChannelResponse actual = service.create(req);

            assertThat(actual).isSameAs(mappedResponse);
            ArgumentCaptor<ChannelCredential> credCap = ArgumentCaptor.forClass(ChannelCredential.class);
            verify(credentialRepository).save(credCap.capture());
            assertThat(credCap.getValue().getConnectionState()).isEqualTo("CONNECTED");
            verify(responseService).toResponse(any(Channel.class));
        }

        @Test
        @DisplayName("update merges metadata, applies displayName + syncEnabled, persists")
        void update_mergesAndSaves() {
                UUID id = UUID.randomUUID();
                Channel existing = Channel.builder()
                                .id(id)
                                .platform(PlatformType.LAZADA)
                                .displayName("Old")
                                .metadata(new java.util.HashMap<>(Map.of("accountId", "acc-1", "shopDomain", "old")))
                                .syncEnabled(false)
                                .build();
                ChannelRequest req = ChannelRequest.builder()
                                .platform(PlatformType.LAZADA)
                                .displayName("New")
                                .metadata(Map.of("accountId", "acc-2", "shopDomain", ""))
                                .commissionRate(new BigDecimal("1.25"))
                                .syncEnabled(true)
                                .build();
                when(channelRepository.findById(id)).thenReturn(Optional.of(existing));

                service.update(id, req);

                assertThat(existing.getDisplayName()).isEqualTo("New");
                assertThat(existing.getSyncEnabled()).isTrue();
                assertThat(existing.getCommissionRate()).isEqualByComparingTo(new BigDecimal("1.25"));
                assertThat(existing.getMetadata()).containsEntry("accountId", "acc-2");
                assertThat(existing.getMetadata()).doesNotContainKey("shopDomain");
                verify(channelRepository).save(existing);
        }

        @Test
        @DisplayName("delete delegates to ChannelConnectionService.disconnect")
        void delete_delegates() {
                UUID id = UUID.randomUUID();
                service.delete(id);

                verify(connectionService).disconnect(id);
                verify(channelRepository, never()).save(any(Channel.class));
        }

        @Test
        @DisplayName("connectLazada delegates to connectionService")
        void connectLazada_delegatesToConnectionService() {
                ChannelResponse expected = ChannelResponse.builder()
                                .id(UUID.randomUUID())
                                .platform(PlatformType.LAZADA)
                                .displayName("Lazada-Demo Lazada")
                                .build();
                when(connectionService.connectLazada("access-tok", "refresh-tok", 3600, 7200000, "acc-001",
                                "Demo Lazada"))
                                .thenReturn(expected);

                ChannelResponse res = service.connectLazada("access-tok", "refresh-tok",
                                3600, 7200000, "acc-001", "Demo Lazada");

                assertThat(res).isSameAs(expected);
                verify(connectionService).connectLazada("access-tok", "refresh-tok", 3600, 7200000, "acc-001",
                                "Demo Lazada");
        }

        @Test
        @DisplayName("getProductChannelSyncs delegates to the query service")
        void getProductChannelSyncs_delegates() {
                Set<UUID> ids = Set.of(UUID.randomUUID());
                Map<UUID, List<ChannelSyncResponse>> expected = Map.of();
                when(productQueryService.getProductChannelSyncs(ids)).thenReturn(expected);

                Map<UUID, List<ChannelSyncResponse>> actual = service.getProductChannelSyncs(ids);

                assertThat(actual).isSameAs(expected);
                verify(productQueryService).getProductChannelSyncs(ids);
        }

        @Test
        @DisplayName("getProductChannels passes ids through to the query service")
        void getProductChannels_delegates() {
                java.util.Collection<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());
                Map<UUID, List<String>> expected = Map.of();
                when(productQueryService.getProductChannels(ids)).thenReturn(expected);

                Map<UUID, List<String>> actual = service.getProductChannels(ids);

                assertThat(actual).isSameAs(expected);
                verify(productQueryService).getProductChannels(ids);
        }
}
