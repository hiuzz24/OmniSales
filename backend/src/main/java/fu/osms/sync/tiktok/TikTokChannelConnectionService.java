package fu.osms.sync.tiktok;

import fu.osms.channel.dto.response.ChannelResponse;

public interface TikTokChannelConnectionService {
    ChannelResponse connect(String authorizationCode, String state);
}
