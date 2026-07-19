package fu.osms.sync.tiktok;

import fu.osms.sync.tiktok.dto.TikTokTokenData;
import fu.osms.channel.token.dto.PlatformTokenRefreshResult;

public interface TikTokOAuthService {

    TikTokTokenData exchangeToken(String code);

    TikTokTokenData exchangeTokenAndResolveShop(String code);

    PlatformTokenRefreshResult refreshToken(String refreshToken);
}
