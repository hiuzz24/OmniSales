package fu.osms.channel.service;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.sync.dto.shopify.WebhookRegistrationResult;

import java.util.Map;
import java.util.UUID;

public interface ChannelConnectionService {
    ChannelResponse connectShopify(String shop, String accessToken);

    void registerShopifyWebhooks(String shop, String accessToken, UUID channelId);

    ChannelResponse connectLazada(String accessToken, String refreshToken, int expiresIn,
                                  int refreshExpiresIn, String accountId, String accountName);

    ChannelResponse connectTikTok(String accessToken, String refreshToken, int expiresIn,
                                  int refreshExpiresIn, String accountId, String accountName,
                                  Map<String, Object> metadata);

    void updateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result);

    void disconnect(UUID channelId);
}
