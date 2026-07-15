package fu.osms.sync.tiktok;

import java.util.List;
import java.util.Map;

public interface TikTokApiClient {

    Map<String, Object> searchProducts(String accessToken, String shopCipher, String pageToken);

    Map<String, Object> getProduct(String accessToken, String shopCipher, String productId);

    Map<String, Object> searchInventory(String accessToken, String shopCipher, List<String> productIds);

    Map<String, Object> getWarehouses(String accessToken, String shopCipher);

    void updateInventory(String accessToken,
                         String shopCipher,
                         String productId,
                         List<Map<String, Object>> skus);
}
