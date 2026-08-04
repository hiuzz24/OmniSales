package fu.osms.channel.token.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.token.dto.AccessTokenContext;
import fu.osms.channel.token.dto.PlatformTokenRefreshResult;
import fu.osms.channel.token.exception.PlatformAccessTokenExpiredException;
import fu.osms.channel.token.exception.TokenRefreshException;
import fu.osms.channel.token.service.PlatformTokenRefresher;
import fu.osms.channel.token.service.TokenOperation;
import fu.osms.common.enums.PlatformType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChannelTokenServiceImpl Tests")
class ChannelTokenServiceImplTest {

    @Mock private ChannelCredentialRepository credentialRepository;
    @Mock private PlatformTokenRefresher refresher;

    private ChannelTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        // The service uses a TransactionTemplate that just runs the callback directly without a real tx manager.
        TransactionTemplate tx = mock(TransactionTemplate.class);
        org.mockito.Mockito.lenient().when(tx.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        service = new ChannelTokenServiceImpl(
                credentialRepository,
                List.of(refresher),
                tx);
        ReflectionTestUtils.setField(service, "refreshBeforeMinutes", 30L);
    }

    private Channel channel(PlatformType platform) {
        Channel ch = Channel.builder()
                .id(UUID.randomUUID())
                .platform(platform)
                .displayName(platform.name())
                .metadata(new HashMap<>())
                .build();
        return ch;
    }

    private ChannelCredential credential(Channel channel, String accessToken, String refreshToken,
                                        OffsetDateTime tokenExpiresAt, OffsetDateTime refreshTokenExpiresAt) {
        return ChannelCredential.builder()
                .channel(channel)
                .connectionState("CONNECTED")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenExpiresAt(tokenExpiresAt)
                .refreshTokenExpiresAt(refreshTokenExpiresAt)
                .build();
    }

    @Test
    @DisplayName("getValidToken returns the current token when it is not yet close to expiry")
    void getValidToken_returnsCurrentToken() {
        Channel channel = channel(PlatformType.LAZADA);
        ChannelCredential cred = credential(channel, "access-1", "refresh-1",
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusDays(7));
        when(credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(cred));

        AccessTokenContext ctx = service.getValidToken(channel.getId());

        assertThat(ctx.accessToken()).isEqualTo("access-1");
        assertThat(ctx.platform()).isEqualTo(PlatformType.LAZADA);
    }

    @Test
    @DisplayName("getValidToken refreshes proactively when the token is within the refresh window")
    void getValidToken_refreshesWhenInRefreshWindow() {
        Channel channel = channel(PlatformType.LAZADA);
        ChannelCredential cred = credential(channel, "access-old", "refresh-token",
                OffsetDateTime.now().plusMinutes(5), // within 30-minute refresh window
                OffsetDateTime.now().plusDays(7));
        when(credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(cred));
        when(refresher.supports(PlatformType.LAZADA)).thenReturn(true);
        when(refresher.refresh("refresh-token")).thenReturn(PlatformTokenRefreshResult.builder()
                .accessToken("access-new")
                .refreshToken("refresh-new")
                .tokenExpiresAt(OffsetDateTime.now().plusHours(1))
                .refreshTokenExpiresAt(OffsetDateTime.now().plusDays(7))
                .build());
        when(credentialRepository.findByChannelIdForUpdate(channel.getId())).thenReturn(Optional.of(cred));

        AccessTokenContext ctx = service.getValidToken(channel.getId());

        assertThat(ctx.accessToken()).isEqualTo("access-new");
        assertThat(cred.getAccessToken()).isEqualTo("access-new");
    }

    @Test
    @DisplayName("getValidToken: SHOPIFY tokens never trigger a refresh (no expiry tracked by this service)")
    void getValidToken_shopifyNeverRefreshes() {
        Channel channel = channel(PlatformType.SHOPIFY);
        ChannelCredential cred = credential(channel, "shpat-1", null,
                OffsetDateTime.now().minusYears(1), null); // ancient expiry
        when(credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(cred));

        AccessTokenContext ctx = service.getValidToken(channel.getId());

        assertThat(ctx.accessToken()).isEqualTo("shpat-1");
    }

    @Test
    @DisplayName("getValidToken: throws IllegalStateException when the channel is not CONNECTED")
    void getValidToken_notConnected() {
        UUID channelId = UUID.randomUUID();
        when(credentialRepository.findByChannelIdAndConnectionState(channelId, "CONNECTED"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getValidToken(channelId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Connected channel credential not found");
    }

    @Test
    @DisplayName("forceRefresh always refreshes regardless of token expiry")
    void forceRefresh() {
        Channel channel = channel(PlatformType.LAZADA);
        ChannelCredential cred = credential(channel, "access-old", "refresh-token",
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusDays(7));
        when(credentialRepository.findByChannelIdForUpdate(channel.getId())).thenReturn(Optional.of(cred));
        when(refresher.supports(PlatformType.LAZADA)).thenReturn(true);
        when(refresher.refresh("refresh-token")).thenReturn(PlatformTokenRefreshResult.builder()
                .accessToken("access-forced")
                .tokenExpiresAt(OffsetDateTime.now().plusHours(1))
                .build());

        AccessTokenContext ctx = service.forceRefresh(channel.getId());

        assertThat(ctx.accessToken()).isEqualTo("access-forced");
    }

    @Test
    @DisplayName("forceRefresh marks the credential as TOKEN_EXPIRED when the refresh token is missing")
    void forceRefresh_missingRefreshToken() {
        Channel channel = channel(PlatformType.LAZADA);
        ChannelCredential cred = credential(channel, "access-1", null, null, null);
        when(credentialRepository.findByChannelIdForUpdate(channel.getId())).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> service.forceRefresh(channel.getId()))
                .isInstanceOf(TokenRefreshException.class)
                .hasMessageContaining("Refresh token is missing");
        assertThat(cred.getConnectionState()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    @DisplayName("execute retries the operation once with a refreshed token if it raises PlatformAccessTokenExpiredException")
    void execute_retriesOnExpired() {
        Channel channel = channel(PlatformType.LAZADA);
        ChannelCredential cred = credential(channel, "access-old", "refresh-token",
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusDays(7));
        when(credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(cred));
        when(credentialRepository.findByChannelIdForUpdate(channel.getId())).thenReturn(Optional.of(cred));
        when(refresher.supports(PlatformType.LAZADA)).thenReturn(true);
        when(refresher.refresh("refresh-token")).thenReturn(PlatformTokenRefreshResult.builder()
                .accessToken("access-new")
                .tokenExpiresAt(OffsetDateTime.now().plusHours(1))
                .build());

        AtomicReference<String> lastToken = new AtomicReference<>();
        TokenOperation<String> op = ctx -> {
            lastToken.set(ctx.accessToken());
            if ("access-old".equals(ctx.accessToken())) {
                throw new PlatformAccessTokenExpiredException("expired");
            }
            return "ok-" + ctx.accessToken();
        };

        String result = service.execute(channel.getId(), op);

        assertThat(result).isEqualTo("ok-access-new");
        assertThat(lastToken.get()).isEqualTo("access-new");
    }

    @Test
    @DisplayName("execute does not retry when the operation succeeds on the first try")
    void execute_doesNotRetryOnSuccess() {
        Channel channel = channel(PlatformType.LAZADA);
        ChannelCredential cred = credential(channel, "access-1", "refresh",
                OffsetDateTime.now().plusHours(1), OffsetDateTime.now().plusDays(7));
        when(credentialRepository.findByChannelIdAndConnectionState(channel.getId(), "CONNECTED"))
                .thenReturn(Optional.of(cred));

        TokenOperation<String> op = ctx -> "ok";

        assertThat(service.execute(channel.getId(), op)).isEqualTo("ok");
    }
}
