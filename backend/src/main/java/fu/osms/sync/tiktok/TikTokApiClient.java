package fu.osms.sync.tiktok;

import java.util.List;
import java.util.Map;
import fu.osms.sync.tiktok.dto.TikTokAuthorizedShop;

public interface TikTokApiClient {
    String executeGet(String apiPath, Map<String, String> queryParams, String accessToken);

    String executePost(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken);
    Map<String, Object> searchProducts(String accessToken, String shopCipher, String pageToken);

    String executePut(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken);
    Map<String, Object> getProduct(String accessToken, String shopCipher, String productId);

    String uploadProductImage(String imageUrl, String useCase, String accessToken);
    Map<String, Object> searchInventory(String accessToken, String shopCipher, List<String> productIds);

    List<TikTokAuthorizedShop> getAuthorizedShops(String accessToken);
    Map<String, Object> getWarehouses(String accessToken, String shopCipher);

    void updateInventory(String accessToken,
                         String shopCipher,
                         String productId,
                         List<Map<String, Object>> skus);
}
