package fu.osms.channel.service.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.event.ChannelDisconnectedEvent;
import fu.osms.channel.repository.ChannelRepository;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.service.ChannelCredentialLifecycleService;
import fu.osms.channel.service.ChannelMappingLifecycleService;
import fu.osms.channel.service.ChannelReconnectResolver;
import fu.osms.channel.service.ChannelResponseService;
import fu.osms.channel.service.model.MappingRestoreResult;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import fu.osms.sync.shopify.ShopifyWebhookSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelConnectionServiceImpl Tests")
class ChannelConnectionServiceImplTest {

    @Mock private ChannelRepository channelRepository;
    @Mock private ChannelReconnectResolver reconnectResolver;
    @Mock private ChannelCredentialLifecycleService credentialLifecycleService;
    @Mock private ChannelMappingLifecycleService mappingLifecycleService;
    @Mock private ChannelConnectionLogService connectionLogService;
    @Mock private ChannelResponseService responseService;
    @Mock private ShopifyShopDomainNormalizer shopDomainNormalizer;
    @Mock private ShopifyWebhookSubscriptionService shopifyWebhookSubscriptionService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ChannelConnectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChannelConnectionServiceImpl(
                channelRepository, reconnectResolver, credentialLifecycleService,
                mappingLifecycleService, connectionLogService, responseService,
                shopDomainNormalizer, shopifyWebhookSubscriptionService, eventPublisher);
    }

    @Test
    @DisplayName("connectShopify: resolve -> activate -> save channel -> connect credential -> log success -> return response")
    void connectShopify_happyPath() {
        String shop = "osms-dev.myshopify.com";
        when(shopDomainNormalizer.normalizeHandle(shop)).thenReturn("osms-dev");

        UUID newChannelId = UUID.randomUUID();
        Channel resolved = Channel.builder().platform(PlatformType.SHOPIFY).build(); // id=null → CONNECT
        when(reconnectResolver.resolveShopify("osms-dev")).thenReturn(resolved);
        when(responseService.toResponse(any(Channel.class))).thenReturn(ChannelResponse.builder().id(newChannelId).build());

        ChannelResponse result = service.connectShopify(shop, "shpat_token");

        assertThat(result).isNotNull();
        assertThat(resolved.getStatus()).isEqualTo("CONNECTED");
        assertThat(resolved.getDisplayName()).isEqualTo("osms-dev");
        assertThat(resolved.getSyncEnabled()).isTrue();
        verify(channelRepository).save(resolved);
        verify(credentialLifecycleService).connectShopify(resolved, "shpat_token");
        verify(connectionLogService).logSuccess(eq(resolved), eq(ChannelConnectionAction.CONNECT), anyString(), any());
    }

    @Test
    @DisplayName("connectShopify uses RECONNECT action when the resolved channel already has an id (soft-deleted)")
    void connectShopify_isReconnect() {
        String shop = "x.myshopify.com";
        when(shopDomainNormalizer.normalizeHandle(shop)).thenReturn("x");
        Channel resolved = Channel.builder()
                .id(UUID.randomUUID())
                .platform(PlatformType.SHOPIFY)
                .deletedAt(java.time.OffsetDateTime.now().minusDays(1))
                .metadata(new HashMap<>())
                .build();
        when(reconnectResolver.resolveShopify("x")).thenReturn(resolved);
        when(mappingLifecycleService.restoreAfterReconnect(resolved.getId()))
                .thenReturn(new MappingRestoreResult(5, false));
        when(responseService.toResponse(any(Channel.class))).thenReturn(ChannelResponse.builder().build());

        service.connectShopify(shop, "shpat_reconnect_token");

        assertThat(resolved.getStatus()).isEqualTo("CONNECTED");
        assertThat(resolved.getDeletedAt()).isNull();
        verify(mappingLifecycleService).restoreAfterReconnect(resolved.getId());
        verify(connectionLogService).logSuccess(eq(resolved), eq(ChannelConnectionAction.RECONNECT), anyString(), any());
        // The connection metadata should report the restore count.
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> metaCaptor =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(connectionLogService).logSuccess(eq(resolved), eq(ChannelConnectionAction.RECONNECT), anyString(), metaCaptor.capture());
        assertThat(metaCaptor.getValue()).containsEntry("restoredMappingCount", 5);
        assertThat(metaCaptor.getValue()).containsEntry("legacyMappingRestore", false);
    }

    @Test
    @DisplayName("disconnect: archives mappings, marks channel DISCONNECTED, clears sync, publishes event")
    void disconnect_happyPath() {
        UUID channelId = UUID.randomUUID();
        Channel channel = Channel.builder()
                .id(channelId)
                .platform(PlatformType.LAZADA)
                .displayName("Laz-1")
                .status("CONNECTED")
                .metadata(new HashMap<>())
                .build();
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
        when(mappingLifecycleService.archiveForDisconnect(channelId)).thenReturn(3);

        service.disconnect(channelId);

        assertThat(channel.getStatus()).isEqualTo("DISCONNECTED");
        assertThat(channel.getSyncEnabled()).isFalse();
        assertThat(channel.getDeletedAt()).isNotNull();
        verify(channelRepository).save(channel);
        verify(mappingLifecycleService).archiveForDisconnect(channelId);
        verify(credentialLifecycleService).disconnect(channelId);
        verify(connectionLogService).logSuccess(eq(channel), eq(ChannelConnectionAction.DISCONNECT), anyString(), any());
        verify(eventPublisher).publishEvent(any(ChannelDisconnectedEvent.class));
    }

    @Test
    @DisplayName("disconnect: throws AppException when no channel exists for the id")
    void disconnect_unknownChannel() {
        UUID channelId = UUID.randomUUID();
        when(channelRepository.findById(channelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(channelId)).isInstanceOf(AppException.class);
        verify(mappingLifecycleService, never()).archiveForDisconnect(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("disconnect: throws AppException when channel has been disconnected / soft-deleted already")
    void disconnect_alreadyDisconnected() {
        UUID channelId = UUID.randomUUID();
        Channel channel = Channel.builder()
                .id(channelId)
                .platform(PlatformType.LAZADA)
                .status("DISCONNECTED")
                .build();
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));

        assertThatThrownBy(() -> service.disconnect(channelId)).isInstanceOf(AppException.class);
        verify(mappingLifecycleService, never()).archiveForDisconnect(any());
    }
}
