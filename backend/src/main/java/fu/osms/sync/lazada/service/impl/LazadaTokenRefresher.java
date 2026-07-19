package fu.osms.sync.lazada.service.impl;

import fu.osms.channel.token.dto.PlatformTokenRefreshResult;
import fu.osms.channel.token.service.PlatformTokenRefresher;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.lazada.service.LazadaOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LazadaTokenRefresher implements PlatformTokenRefresher {
    private final LazadaOAuthService oauthService;

    @Override
    public boolean supports(PlatformType platform) {
        return platform == PlatformType.LAZADA;
    }

    @Override
    public PlatformTokenRefreshResult refresh(String refreshToken) {
        return oauthService.refreshToken(refreshToken);
    }
}
