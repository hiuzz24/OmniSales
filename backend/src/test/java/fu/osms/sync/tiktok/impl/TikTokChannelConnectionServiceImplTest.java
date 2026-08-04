package fu.osms.sync.tiktok.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.sync.tiktok.TikTokOAuthService;
import fu.osms.sync.tiktok.dto.TikTokTokenData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TikTokChannelConnectionServiceImpl Tests")
class TikTokChannelConnectionServiceImplTest {

    @Mock private TikTokOAuthService tikTokOAuthService;
    @Mock private ChannelService channelService;

    private TikTokChannelConnectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TikTokChannelConnectionServiceImpl(tikTokOAuthService, channelService);
    }

    private TikTokTokenData token(Map<String, Object> metadata) {
        return TikTokTokenData.builder()
                .accessToken("access-tok")
                .refreshToken("refresh-tok")
                .expiresInSeconds(3600)
                .refreshExpiresInSeconds(86400)
                .accountId("acc-1")
                .accountName("My Shop")
                .metadata(metadata)
                .build();
    }

    @Test
    @DisplayName("connect: exchanges code, adds state to metadata, and delegates to channelService.connectTikTok")
    void connect_withState() {
        Map<String, Object> meta = new HashMap<>(Map.of("shopCipher", "cipher-1"));
        when(tikTokOAuthService.exchangeTokenAndResolveShop("code-1"))
                .thenReturn(token(meta));
        ChannelResponse response = ChannelResponse.builder().id(UUID.randomUUID()).build();
        when(channelService.connectTikTok(
                anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(), any()))
                .thenReturn(response);

        ChannelResponse result = service.connect("code-1", "state-XYZ");

        assertThat(result).isSameAs(response);
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(channelService).connectTikTok(
                eq("access-tok"), eq("refresh-tok"),
                eq(3600), eq(86400),
                eq("acc-1"), eq("My Shop"),
                captor.capture());
        assertThat(captor.getValue()).containsEntry("shopCipher", "cipher-1");
        assertThat(captor.getValue()).containsEntry("state", "state-XYZ");
    }

    @Test
    @DisplayName("connect: does not add 'state' entry when state is null or blank")
    void connect_noState() {
        Map<String, Object> meta = new HashMap<>(Map.of("shopCipher", "cipher-1"));
        when(tikTokOAuthService.exchangeTokenAndResolveShop("code-2"))
                .thenReturn(token(meta));
        when(channelService.connectTikTok(
                anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(), any()))
                .thenReturn(ChannelResponse.builder().build());

        service.connect("code-2", null);
        service.connect("code-2", "");
        service.connect("code-2", "   ");

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(channelService, org.mockito.Mockito.times(3)).connectTikTok(
                anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(),
                captor.capture());
        for (Map<String, Object> arg : captor.getAllValues()) {
            assertThat(arg).doesNotContainKey("state");
            assertThat(arg).containsEntry("shopCipher", "cipher-1");
        }
    }

    @Test
    @DisplayName("connect: forwards all token fields to channelService.connectTikTok")
    void connect_forwardsAllFields() {
        Map<String, Object> meta = new HashMap<>();
        when(tikTokOAuthService.exchangeTokenAndResolveShop("code-4"))
                .thenReturn(token(meta));
        when(channelService.connectTikTok(
                anyString(), anyString(), anyInt(), anyInt(), anyString(), anyString(), any()))
                .thenReturn(ChannelResponse.builder().build());

        service.connect("code-4", null);

        verify(channelService).connectTikTok(
                eq("access-tok"), eq("refresh-tok"),
                eq(3600), eq(86400),
                eq("acc-1"), eq("My Shop"),
                eq(new HashMap<>()));
    }

    @Test
    @DisplayName("connect: throws NullPointerException when token metadata is null (documents current behaviour)")
    void connect_nullMetadata() {
        when(tikTokOAuthService.exchangeTokenAndResolveShop("code-3"))
                .thenReturn(token(null));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.connect("code-3", "state-OK"))
                .isInstanceOf(NullPointerException.class);
    }
}
