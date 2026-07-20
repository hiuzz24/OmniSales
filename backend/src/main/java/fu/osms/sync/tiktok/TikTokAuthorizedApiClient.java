package fu.osms.sync.tiktok;

import fu.osms.channel.token.service.ChannelTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TikTokAuthorizedApiClient {
    private final TikTokApiClient apiClient;
    private final ChannelTokenService tokenService;

    public String executeGet(UUID channelId, String path, Map<String, String> query) {
        return tokenService.execute(channelId,
                token -> apiClient.executeGet(path, query, token.accessToken()));
    }

    public String executePost(UUID channelId, String path, Map<String, String> query, String rawBody) {
        return tokenService.execute(channelId,
                token -> apiClient.executePost(path, query, rawBody, token.accessToken()));
    }

    public String executePut(UUID channelId, String path, Map<String, String> query, String rawBody) {
        return tokenService.execute(channelId,
                token -> apiClient.executePut(path, query, rawBody, token.accessToken()));
    }

    public String uploadProductImage(UUID channelId, String imageUrl, String useCase) {
        return tokenService.execute(channelId,
                token -> apiClient.uploadProductImage(imageUrl, useCase, token.accessToken()));
    }

    public Map<String, Object> searchProducts(UUID channelId, String shopCipher, String pageToken) {
        return tokenService.execute(channelId,
                token -> apiClient.searchProducts(token.accessToken(), shopCipher, pageToken));
    }

    public Map<String, Object> getProduct(UUID channelId, String shopCipher, String productId) {
        return tokenService.execute(channelId,
                token -> apiClient.getProduct(token.accessToken(), shopCipher, productId));
    }

    public Map<String, Object> searchInventory(UUID channelId, String shopCipher, List<String> productIds) {
        return tokenService.execute(channelId,
                token -> apiClient.searchInventory(token.accessToken(), shopCipher, productIds));
    }

    public Map<String, Object> getWarehouses(UUID channelId, String shopCipher) {
        return tokenService.execute(channelId,
                token -> apiClient.getWarehouses(token.accessToken(), shopCipher));
    }

    public void updateInventory(UUID channelId, String shopCipher, String productId,
                                List<Map<String, Object>> skus) {
        tokenService.execute(channelId, token -> {
            apiClient.updateInventory(token.accessToken(), shopCipher, productId, skus);
            return null;
        });
    }
}
