package fu.osms.sync.tiktok;

import fu.osms.sync.tiktok.dto.TikTokTokenData;

public interface TikTokOAuthService {

    TikTokTokenData exchangeToken(String code);

    TikTokTokenData exchangeTokenAndResolveShop(String code);
}
