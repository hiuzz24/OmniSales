package fu.osms.channel.service;

import fu.osms.channel.entity.Channel;

public interface ChannelReconnectResolver {

    Channel resolveShopify(String shopHandle);

    Channel resolveLazada(String accountId, String displayName);

    Channel resolveTikTok(String shopId, String openId, String accountId, String displayName);
}
