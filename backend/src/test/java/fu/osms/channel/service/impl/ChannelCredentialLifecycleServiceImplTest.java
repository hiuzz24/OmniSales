package fu.osms.channel.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelCredentialLifecycleServiceImpl Tests")
class ChannelCredentialLifecycleServiceImplTest {

    @Mock private ChannelCredentialRepository credentialRepository;

    private ChannelCredentialLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ChannelCredentialLifecycleServiceImpl(credentialRepository);
    }

    private Channel channel() {
        UUID channelId = UUID.randomUUID();
        return Channel.builder().id(channelId).platform(fu.osms.common.enums.PlatformType.SHOPIFY).build();
    }

    @Test
    @DisplayName("connectShopify updates an existing credential in-place and saves it with state=CONNECTED")
    void connectShopify_updatesExisting() {
        Channel ch = channel();
        ChannelCredential existing = ChannelCredential.builder().channel(ch).connectionState("DISCONNECTED").build();
        when(credentialRepository.findByChannelId(ch.getId())).thenReturn(Optional.of(existing));

        service.connectShopify(ch, "shpat_new_token");

        assertThat(existing.getAccessToken()).isEqualTo("shpat_new_token");
        assertThat(existing.getConnectionState()).isEqualTo("CONNECTED");
        assertThat(existing.getRefreshError()).isNull();
        verify(credentialRepository).save(existing);
    }

    @Test
    @DisplayName("connectShopify creates a fresh credential when none exists for the channel")
    void connectShopify_createsNew() {
        Channel ch = channel();
        when(credentialRepository.findByChannelId(ch.getId())).thenReturn(Optional.empty());

        service.connectShopify(ch, "shpat_brand_new");

        ArgumentCaptor<ChannelCredential> captor = ArgumentCaptor.forClass(ChannelCredential.class);
        verify(credentialRepository).save(captor.capture());
        ChannelCredential saved = captor.getValue();
        assertThat(saved.getChannel()).isEqualTo(ch);
        assertThat(saved.getAccessToken()).isEqualTo("shpat_brand_new");
        assertThat(saved.getConnectionState()).isEqualTo("CONNECTED");
    }

    @Test
    @DisplayName("connectRefreshable stores both tokens and sets expiry timestamps when expiresIn > 0")
    void connectRefreshable_setsExpiries() {
        Channel ch = channel();
        when(credentialRepository.findByChannelId(ch.getId())).thenReturn(Optional.empty());

        service.connectRefreshable(ch, "access-1", "refresh-1", 3600, 86400);

        ArgumentCaptor<ChannelCredential> captor = ArgumentCaptor.forClass(ChannelCredential.class);
        verify(credentialRepository).save(captor.capture());
        ChannelCredential saved = captor.getValue();
        assertThat(saved.getAccessToken()).isEqualTo("access-1");
        assertThat(saved.getRefreshToken()).isEqualTo("refresh-1");
        assertThat(saved.getConnectionState()).isEqualTo("CONNECTED");
        assertThat(saved.getTokenExpiresAt()).isNotNull();
        assertThat(saved.getRefreshTokenExpiresAt()).isNotNull();
        assertThat(saved.getLastRefreshedAt()).isNotNull();
        assertThat(saved.getRefreshError()).isNull();
    }

    @Test
    @DisplayName("connectRefreshable leaves expiry fields null when expiresIn and refreshExpiresIn are <= 0")
    void connectRefreshable_noExpiries() {
        Channel ch = channel();
        when(credentialRepository.findByChannelId(ch.getId())).thenReturn(Optional.empty());

        service.connectRefreshable(ch, "a", "r", 0, 0);

        ArgumentCaptor<ChannelCredential> captor = ArgumentCaptor.forClass(ChannelCredential.class);
        verify(credentialRepository).save(captor.capture());
        ChannelCredential saved = captor.getValue();
        assertThat(saved.getTokenExpiresAt()).isNull();
        assertThat(saved.getRefreshTokenExpiresAt()).isNull();
        assertThat(saved.getLastRefreshedAt()).isNotNull();
    }

    @Test
    @DisplayName("disconnect sets state=DISCONNECTED on the existing credential, when one exists")
    void disconnect_setsDisconnected() {
        Channel ch = channel();
        ChannelCredential existing = ChannelCredential.builder().channel(ch).connectionState("CONNECTED").build();
        when(credentialRepository.findByChannelId(ch.getId())).thenReturn(Optional.of(existing));

        service.disconnect(ch.getId());

        assertThat(existing.getConnectionState()).isEqualTo("DISCONNECTED");
        verify(credentialRepository).save(existing);
    }

    @Test
    @DisplayName("disconnect does nothing when no credential row exists for the channel")
    void disconnect_noOpWhenMissing() {
        UUID channelId = UUID.randomUUID();
        when(credentialRepository.findByChannelId(channelId)).thenReturn(Optional.empty());

        service.disconnect(channelId);

        verify(credentialRepository, never()).save(any());
    }
}
