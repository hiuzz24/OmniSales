package fu.osms.channel.token.service;

import fu.osms.channel.token.dto.AccessTokenContext;

import java.util.UUID;

public interface ChannelTokenService {
    AccessTokenContext getValidToken(UUID channelId);

    AccessTokenContext forceRefresh(UUID channelId);

    <T> T execute(UUID channelId, TokenOperation<T> operation);
}
