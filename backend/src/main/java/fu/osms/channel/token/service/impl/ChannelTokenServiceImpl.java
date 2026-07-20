package fu.osms.channel.token.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.token.dto.AccessTokenContext;
import fu.osms.channel.token.dto.PlatformTokenRefreshResult;
import fu.osms.channel.token.exception.PlatformAccessTokenExpiredException;
import fu.osms.channel.token.exception.TokenRefreshException;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.channel.token.service.PlatformTokenRefresher;
import fu.osms.channel.token.service.TokenOperation;
import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelTokenServiceImpl implements ChannelTokenService {

    private final ChannelCredentialRepository credentialRepository;
    private final List<PlatformTokenRefresher> refreshers;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.channel-token-refresh.refresh-before-minutes:30}")
    private long refreshBeforeMinutes;

    @Override
    public AccessTokenContext getValidToken(UUID channelId) {
        ChannelCredential credential = connectedCredential(channelId);
        if (!needsRefresh(credential)) {
            return context(credential);
        }
        return refresh(channelId, false, null);
    }

    @Override
    public AccessTokenContext forceRefresh(UUID channelId) {
        return refresh(channelId, true, null);
    }

    @Override
    public <T> T execute(UUID channelId, TokenOperation<T> operation) {
        AccessTokenContext current = getValidToken(channelId);
        try {
            return operation.execute(current);
        } catch (PlatformAccessTokenExpiredException expired) {
            AccessTokenContext refreshed = refresh(channelId, true, current.accessToken());
            return operation.execute(refreshed);
        }
    }

    private AccessTokenContext refresh(UUID channelId, boolean force, String failedAccessToken) {
        RefreshAttempt attempt = transactionTemplate.execute(status -> refreshLocked(
                channelId, force, failedAccessToken));
        if (attempt == null) {
            throw new IllegalStateException("Token refresh transaction returned no result");
        }
        if (attempt.error() != null) {
            throw attempt.error();
        }
        return attempt.context();
    }

    private RefreshAttempt refreshLocked(UUID channelId, boolean force, String failedAccessToken) {
        ChannelCredential credential = credentialRepository.findByChannelIdForUpdate(channelId)
                .orElseThrow(() -> new IllegalStateException("Channel credential not found: " + channelId));
        if (!"CONNECTED".equals(credential.getConnectionState())) {
            return RefreshAttempt.failed(new IllegalStateException(
                    "Channel credential is not connected; reconnect the channel"));
        }
        requireAccessToken(credential);

        if (failedAccessToken != null && !Objects.equals(failedAccessToken, credential.getAccessToken())) {
            return RefreshAttempt.succeeded(context(credential));
        }
        if (!force && !needsRefresh(credential)) {
            return RefreshAttempt.succeeded(context(credential));
        }

        if (credential.getRefreshToken() == null || credential.getRefreshToken().isBlank()) {
            return markFailure(credential, TokenRefreshException.expired(
                    "Refresh token is missing; reconnect the channel"));
        }
        if (credential.getRefreshTokenExpiresAt() != null
                && !credential.getRefreshTokenExpiresAt().isAfter(OffsetDateTime.now())) {
            return markFailure(credential, TokenRefreshException.expired(
                    "Refresh token has expired; reconnect the channel"));
        }

        PlatformTokenRefresher refresher = refreshers.stream()
                .filter(value -> value.supports(credential.getChannel().getPlatform()))
                .findFirst()
                .orElse(null);
        if (refresher == null) {
            return RefreshAttempt.failed(new IllegalStateException(
                    "Token refresh is not supported for " + credential.getChannel().getPlatform()));
        }

        try {
            PlatformTokenRefreshResult result = refresher.refresh(credential.getRefreshToken());
            validateIdentity(credential.getChannel(), result.getAccountId());
            credential.setAccessToken(requireText(result.getAccessToken(), "Refreshed access token is missing"));
            if (result.getRefreshToken() != null && !result.getRefreshToken().isBlank()) {
                credential.setRefreshToken(result.getRefreshToken());
            }
            credential.setTokenExpiresAt(result.getTokenExpiresAt());
            if (result.getRefreshTokenExpiresAt() != null) {
                credential.setRefreshTokenExpiresAt(result.getRefreshTokenExpiresAt());
            }
            credential.setLastRefreshedAt(OffsetDateTime.now());
            credential.setRefreshError(null);
            credential.setConnectionState("CONNECTED");
            updateGrantedScopes(credential.getChannel(), result.getGrantedScopes());
            credential.getChannel().setStatus("CONNECTED");
            credentialRepository.save(credential);
            return RefreshAttempt.succeeded(context(credential));
        } catch (TokenRefreshException error) {
            return markFailure(credential, error);
        } catch (RuntimeException error) {
            return markFailure(credential, TokenRefreshException.transientFailure(
                    "Unable to refresh " + credential.getChannel().getPlatform() + " token", error));
        }
    }

    private RefreshAttempt markFailure(ChannelCredential credential, TokenRefreshException error) {
        credential.setRefreshError(safeMessage(error));
        if (error.isReauthorizationRequired()) {
            credential.setConnectionState(error.isRevoked() ? "REVOKED" : "TOKEN_EXPIRED");
            credential.getChannel().setStatus("ERROR");
        }
        credentialRepository.save(credential);
        return RefreshAttempt.failed(error);
    }

    private ChannelCredential connectedCredential(UUID channelId) {
        ChannelCredential credential = credentialRepository
                .findByChannelIdAndConnectionState(channelId, "CONNECTED")
                .orElseThrow(() -> new IllegalStateException(
                        "Connected channel credential not found: " + channelId));
        requireAccessToken(credential);
        return credential;
    }

    private boolean needsRefresh(ChannelCredential credential) {
        return credential.getChannel().getPlatform() != PlatformType.SHOPIFY
                && credential.getTokenExpiresAt() != null
                && !credential.getTokenExpiresAt().isAfter(
                OffsetDateTime.now().plusMinutes(refreshBeforeMinutes));
    }

    private AccessTokenContext context(ChannelCredential credential) {
        return new AccessTokenContext(
                credential.getChannel().getId(),
                credential.getChannel().getPlatform(),
                credential.getAccessToken(),
                credential.getTokenExpiresAt()
        );
    }

    private void validateIdentity(Channel channel, String refreshedAccountId) {
        if (channel.getPlatform() != PlatformType.TIKTOK
                || refreshedAccountId == null || refreshedAccountId.isBlank()) {
            return;
        }
        Map<String, Object> metadata = channel.getMetadata();
        Object expected = metadata == null ? null
                : metadata.getOrDefault("openId", metadata.get("accountId"));
        if (expected != null && !String.valueOf(expected).isBlank()
                && !Objects.equals(String.valueOf(expected), refreshedAccountId)) {
            throw TokenRefreshException.revoked(
                    "TikTok refresh token belongs to a different authorization; reconnect the channel");
        }
    }

    private void updateGrantedScopes(Channel channel, List<String> grantedScopes) {
        if (grantedScopes == null) {
            return;
        }
        Map<String, Object> metadata = channel.getMetadata() == null
                ? new HashMap<>() : new HashMap<>(channel.getMetadata());
        metadata.put("grantedScopes", grantedScopes);
        channel.setMetadata(metadata);
    }

    private void requireAccessToken(ChannelCredential credential) {
        requireText(credential.getAccessToken(), "Channel access token is missing; reconnect the channel");
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }

    private String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "Token refresh failed";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }

    private record RefreshAttempt(AccessTokenContext context, RuntimeException error) {
        static RefreshAttempt succeeded(AccessTokenContext context) {
            return new RefreshAttempt(context, null);
        }

        static RefreshAttempt failed(RuntimeException error) {
            return new RefreshAttempt(null, error);
        }
    }
}
