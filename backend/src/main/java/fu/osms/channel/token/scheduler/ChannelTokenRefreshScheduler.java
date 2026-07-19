package fu.osms.channel.token.scheduler;

import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.token.service.ChannelTokenService;
import fu.osms.common.enums.PlatformType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.channel-token-refresh", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ChannelTokenRefreshScheduler {

    private final ChannelCredentialRepository credentialRepository;
    private final ChannelTokenService channelTokenService;

    @Value("${app.channel-token-refresh.refresh-before-minutes:30}")
    private long refreshBeforeMinutes;

    @Scheduled(fixedDelayString = "${app.channel-token-refresh.fixed-delay-ms:600000}")
    public void refreshExpiringTokens() {
        List<ChannelCredential> expiring = credentialRepository.findExpiringCredentials(
                List.of(PlatformType.LAZADA, PlatformType.TIKTOK),
                OffsetDateTime.now().plusMinutes(refreshBeforeMinutes)
        );
        for (ChannelCredential credential : expiring) {
            try {
                channelTokenService.getValidToken(credential.getChannel().getId());
                log.info("[ChannelTokenRefresh] Refreshed platform={} channelId={}",
                        credential.getChannel().getPlatform(), credential.getChannel().getId());
            } catch (Exception error) {
                log.warn("[ChannelTokenRefresh] Failed platform={} channelId={}: {}",
                        credential.getChannel().getPlatform(), credential.getChannel().getId(),
                        error.getMessage());
            }
        }
    }
}
