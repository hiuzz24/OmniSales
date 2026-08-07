package fu.osms.channel.service;

import fu.osms.channel.entity.Channel;

import java.util.UUID;

public interface ChannelCredentialLifecycleService {

    void connectShopify(Channel channel, String accessToken);

    void connectRefreshable(Channel channel, String accessToken, String refreshToken,
                            int expiresIn, int refreshExpiresIn);

    void disconnect(UUID channelId);
}
