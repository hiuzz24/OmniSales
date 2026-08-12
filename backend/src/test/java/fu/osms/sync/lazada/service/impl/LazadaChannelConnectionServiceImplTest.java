package fu.osms.sync.lazada.service.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.lazada.service.LazadaOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LazadaChannelConnectionServiceImplTest {

    @Mock private LazadaOAuthService lazadaOAuthService;
    @Mock private ChannelService channelService;

    private LazadaChannelConnectionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LazadaChannelConnectionServiceImpl(lazadaOAuthService, channelService);
    }

    @Test
    void buildAuthorizationUrl_delegatesToOAuthService() {
        when(lazadaOAuthService.buildAuthorizationUrl()).thenReturn("https://auth.lazada.test");

        assertThat(service.buildAuthorizationUrl()).isEqualTo("https://auth.lazada.test");
    }

    @Test
    void connect_forwardsTokenAndAccountData() {
        Map<String, Object> tokenData = Map.of(
                "access_token", "access-token",
                "refresh_token", "refresh-token",
                "expires_in", "3600",
                "refresh_expires_in", 86400,
                "account_id", "seller-1",
                "account_name", "Seller One"
        );
        ChannelResponse expected = ChannelResponse.builder().displayName("Seller One").build();
        when(lazadaOAuthService.exchangeToken("code")).thenReturn(tokenData);
        when(channelService.connectLazada(
                "access-token", "refresh-token", 3600, 86400, "seller-1", "Seller One"))
                .thenReturn(expected);

        ChannelResponse actual = service.connect("code");

        assertThat(actual).isSameAs(expected);
    }

    @Test
    void connect_usesDefaultExpiryValuesWhenResponseIsInvalid() {
        Map<String, Object> tokenData = Map.of(
                "access_token", "access-token",
                "expires_in", "invalid",
                "refresh_expires_in", "invalid"
        );
        when(lazadaOAuthService.exchangeToken("code")).thenReturn(tokenData);

        service.connect("code");

        verify(channelService).connectLazada("access-token", null, 604800, 0, null, null);
    }

    @Test
    void connect_rejectsResponseWithoutAccessToken() {
        when(lazadaOAuthService.exchangeToken("code")).thenReturn(Map.of("error", "invalid_grant"));

        assertThatThrownBy(() -> service.connect("code"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("access_token");
        verifyNoInteractions(channelService);
    }

    @Test
    void connect_propagatesOAuthFailure() {
        RuntimeException failure = new RuntimeException("OAuth failed");
        when(lazadaOAuthService.exchangeToken("code")).thenThrow(failure);

        assertThatThrownBy(() -> service.connect("code")).isSameAs(failure);
        verifyNoInteractions(channelService);
    }

    @Test
    void connect_propagatesIdentityConflict() {
        when(lazadaOAuthService.exchangeToken("code"))
                .thenReturn(Map.of("access_token", "access-token"));
        AppException conflict = new AppException(ErrorCode.CHANNEL_IDENTITY_CONFLICT, "Conflict");
        when(channelService.connectLazada("access-token", null, 604800, 0, null, null))
                .thenThrow(conflict);

        assertThatThrownBy(() -> service.connect("code")).isSameAs(conflict);
    }
}
