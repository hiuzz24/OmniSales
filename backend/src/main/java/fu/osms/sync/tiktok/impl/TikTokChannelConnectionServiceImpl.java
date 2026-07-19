package fu.osms.sync.tiktok.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.sync.tiktok.TikTokChannelConnectionService;
import fu.osms.sync.tiktok.TikTokOAuthService;
import fu.osms.sync.tiktok.dto.TikTokTokenData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TikTokChannelConnectionServiceImpl implements TikTokChannelConnectionService {

    private final TikTokOAuthService tikTokOAuthService;
    private final ChannelService channelService;

    @Override
    public ChannelResponse connect(String authorizationCode, String state) {
        TikTokTokenData token = tikTokOAuthService.exchangeTokenAndResolveShop(authorizationCode);
        Map<String, Object> metadata = new HashMap<>(token.getMetadata());
        if (state != null && !state.isBlank()) {
            metadata.put("state", state);
        }
        return channelService.connectTikTok(
                token.getAccessToken(),
                token.getRefreshToken(),
                token.getExpiresInSeconds(),
                token.getRefreshExpiresInSeconds(),
                token.getAccountId(),
                token.getAccountName(),
                metadata
        );
    }
}
