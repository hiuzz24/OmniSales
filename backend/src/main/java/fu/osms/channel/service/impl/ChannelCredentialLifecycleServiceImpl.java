package fu.osms.channel.service.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.service.ChannelCredentialLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChannelCredentialLifecycleServiceImpl implements ChannelCredentialLifecycleService {

    private final ChannelCredentialRepository credentialRepository;

    @Override
    public void connectShopify(Channel channel, String accessToken) {
        ChannelCredential credential = credential(channel);
        credential.setAccessToken(accessToken);
        credential.setConnectionState("CONNECTED");
        credential.setRefreshError(null);
        credentialRepository.save(credential);
    }

    @Override
    public void connectRefreshable(Channel channel, String accessToken, String refreshToken,
                                   int expiresIn, int refreshExpiresIn) {
        ChannelCredential credential = credential(channel);
        OffsetDateTime now = OffsetDateTime.now();
        credential.setAccessToken(accessToken);
        credential.setRefreshToken(refreshToken);
        credential.setConnectionState("CONNECTED");
        credential.setTokenExpiresAt(expiresIn > 0 ? now.plusSeconds(expiresIn) : null);
        credential.setRefreshTokenExpiresAt(refreshExpiresIn > 0 ? now.plusSeconds(refreshExpiresIn) : null);
        credential.setLastRefreshedAt(now);
        credential.setRefreshError(null);
        credentialRepository.save(credential);
    }

    @Override
    public void disconnect(UUID channelId) {
        credentialRepository.findByChannelId(channelId).ifPresent(credential -> {
            credential.setConnectionState("DISCONNECTED");
            credentialRepository.save(credential);
        });
    }

    private ChannelCredential credential(Channel channel) {
        return credentialRepository.findByChannelId(channel.getId())
                .orElseGet(() -> ChannelCredential.builder().channel(channel).build());
    }
}
