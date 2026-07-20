package fu.osms.channel.service;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.request.CreateManualChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.common.dto.PageResponse;

import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.sync.dto.shopify.WebhookRegistrationResult;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChannelService {

    ChannelResponse create(ChannelRequest request);

    ChannelResponse getById(UUID id);

    List<ChannelResponse> getAll();

    ChannelResponse update(UUID id, ChannelRequest request);

    void delete(UUID id);

    PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size);

    Map<UUID, List<String>> getProductChannels(Collection<UUID> productIds);

    Map<UUID, List<UUID>> getProductChannelIds(Collection<UUID> productIds);

    Map<UUID, List<ChannelSyncResponse>> getProductChannelSyncs(Collection<UUID> productIds);

    ChannelResponse connectShopify(String shop, String accessToken);

    void registerShopifyWebhooks(String shop, String accessToken, UUID channelId);

    ChannelResponse connectLazada(String accessToken, String refreshToken, int expiresIn,
                                  int refreshExpiresIn, String accountId, String accountName);

    ChannelResponse connectTikTok(String accessToken, String refreshToken, int expiresIn,
                                  int refreshExpiresIn, String accountId, String accountName,
                                  Map<String, Object> metadata);

    void updateShopifyWebhookMetadata(UUID channelId, WebhookRegistrationResult result);
}
