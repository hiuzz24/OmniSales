package fu.osms.sync.tiktok.impl;

import fu.osms.channel.token.dto.PlatformTokenRefreshResult;
import fu.osms.channel.token.service.PlatformTokenRefresher;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.tiktok.TikTokOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TikTokTokenRefresher implements PlatformTokenRefresher {
    private final TikTokOAuthService oauthService;

    @Override
    public boolean supports(PlatformType platform) {
        return platform == PlatformType.TIKTOK;
    }

    @Override
    public PlatformTokenRefreshResult refresh(String refreshToken) {
        return oauthService.refreshToken(refreshToken);
    }
}
