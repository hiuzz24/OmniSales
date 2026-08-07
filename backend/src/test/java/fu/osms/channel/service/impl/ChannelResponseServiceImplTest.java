package fu.osms.channel.service.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.mapper.ChannelMapper;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelResponseServiceImpl Tests")
class ChannelResponseServiceImplTest {

    @Mock private ChannelMapper channelMapper;
    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private ChannelProductRepository channelProductRepository;
    @Mock private ChannelProductVariantRepository channelProductVariantRepository;

    private ChannelResponseServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChannelResponseServiceImpl(
                channelMapper, credentialRepository, channelProductRepository, channelProductVariantRepository);
        ReflectionTestUtils.setField(service, "lazadaWebhookCallbackUrl", "https://hooks.osms/lazada/cb");
    }

    @Test
    @DisplayName("enrichStats sets productCount and skuVariantCount on a non-LAZADA channel")
    void enrichStats_nonLazada_setsCounts() {
        Channel channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .displayName("Shop-1")
                .metadata(new HashMap<>())
                .build();
        when(channelProductRepository.countByChannelIdAndMappingState(channel.getId(), "ACTIVE")).thenReturn(7L);
        when(channelProductVariantRepository.countActiveByChannelId(channel.getId())).thenReturn(21L);

        service.enrichStats(channel);

        Map<String, Object> meta = channel.getMetadata();
        assertThat(meta).        containsEntry("productCount", 7L);
        assertThat(meta).containsEntry("skuVariantCount", 21L);
        assertThat(meta).containsEntry("warehouseCount", 0);
        // SHOPIFY should not get Lazada webhook metadata.
        assertThat(meta).doesNotContainKey("webhookCallbackUrl");
        assertThat(meta).doesNotContainKey("webhookRegistrationStatus");
    }

    @Test
    @DisplayName("enrichStats on LAZADA adds webhook metadata, and callaback url is reflected")
    void enrichStats_lazada_addsWebhookMetadata() {
        Channel channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Laz-1")
                .metadata(new HashMap<>())
                .build();
        when(channelProductRepository.countByChannelIdAndMappingState(channel.getId(), "ACTIVE")).thenReturn(0L);
        when(channelProductVariantRepository.countActiveByChannelId(channel.getId())).thenReturn(0L);

        service.enrichStats(channel);

        Map<String, Object> meta = channel.getMetadata();
        assertThat(meta).containsEntry("webhookCallbackUrl", "https://hooks.osms/lazada/cb");
        assertThat(meta).containsEntry("webhookRegistrationStatus", "MANUAL_CONFIGURATION_REQUIRED");
        assertThat(meta.get("webhookRegistrationNote")).asString().contains("Lazada Open Platform");
    }

    @Test
    @DisplayName("enrichStats reports MISSING_CALLBACK_URL when no callback URL is configured")
    void enrichStats_lazada_missingCallbackUrl() {
        ReflectionTestUtils.setField(service, "lazadaWebhookCallbackUrl", "");
        Channel channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Laz-missing")
                .metadata(new HashMap<>())
                .build();
        when(channelProductRepository.countByChannelIdAndMappingState(any(), any())).thenReturn(0L);
        when(channelProductVariantRepository.countActiveByChannelId(any())).thenReturn(0L);

        service.enrichStats(channel);

        assertThat(channel.getMetadata()).containsEntry("webhookRegistrationStatus", "MISSING_CALLBACK_URL");
        assertThat(channel.getMetadata()).containsEntry("webhookCallbackUrl", "");
    }

    @Test
    @DisplayName("enrichStats does not overwrite warehouseCount if it's already present in metadata")
    void enrichStats_doesNotOverwriteWarehouseCount() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("warehouseCount", 5);
        Channel channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .displayName("S")
                .metadata(existing)
                .build();
        when(channelProductRepository.countByChannelIdAndMappingState(any(), any())).thenReturn(0L);
        when(channelProductVariantRepository.countActiveByChannelId(any())).thenReturn(0L);

        service.enrichStats(channel);

        assertThat(channel.getMetadata().get("warehouseCount")).isEqualTo(5); // unchanged
    }

    @Test
    @DisplayName("enrichStats initializes an empty metadata map when the channel has none")
    void enrichStats_handlesNullMetadata() {
        Channel channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .displayName("S")
                .build(); // no metadata
        when(channelProductRepository.countByChannelIdAndMappingState(any(), any())).thenReturn(0L);
        when(channelProductVariantRepository.countActiveByChannelId(any())).thenReturn(0L);

        service.enrichStats(channel);

        assertThat(channel.getMetadata()).isNotNull();
        assertThat(channel.getMetadata()).containsEntry("productCount", 0L);
    }

    @Test
    @DisplayName("toResponse delegates to ChannelMapper and overlays credential state when a credential exists")
    void toResponse_overlaysCredentialState() {
        Channel channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.LAZADA)
                .displayName("Laz-1")
                .build();
        ChannelResponse stub = ChannelResponse.builder()
                .id(channel.getId())
                .displayName("Laz-1")
                .platform(PlatformType.LAZADA)
                .build();
        when(channelMapper.toResponse(channel)).thenReturn(stub);
        OffsetDateTime now = OffsetDateTime.now();
        ChannelCredential cred = ChannelCredential.builder()
                .channel(channel)
                .connectionState("CONNECTED")
                .tokenExpiresAt(now)
                .refreshTokenExpiresAt(now.plusDays(7))
                .refreshError("none")
                .build();
        when(credentialRepository.findByChannelId(channel.getId())).thenReturn(Optional.of(cred));

        ChannelResponse out = service.toResponse(channel);

        assertThat(out.getConnectionState()).isEqualTo("CONNECTED");
        assertThat(out.getTokenExpiresAt()).isEqualTo(now);
        assertThat(out.getRefreshTokenExpiresAt()).isEqualTo(now.plusDays(7));
        assertThat(out.getRefreshError()).isEqualTo("none");
    }

    @Test
    @DisplayName("toResponse leaves credential fields null when no credential exists")
    void toResponse_noCredential() {
        Channel channel = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .displayName("S")
                .build();
        ChannelResponse stub = ChannelResponse.builder()
                .id(channel.getId())
                .displayName("S")
                .platform(PlatformType.SHOPIFY)
                .build();
        when(channelMapper.toResponse(channel)).thenReturn(stub);
        when(credentialRepository.findByChannelId(channel.getId())).thenReturn(Optional.empty());

        ChannelResponse out = service.toResponse(channel);

        assertThat(out.getConnectionState()).isNull();
        assertThat(out.getTokenExpiresAt()).isNull();
        assertThat(out.getRefreshError()).isNull();
    }
}
