package fu.osms.sync.tiktok;

import java.util.Map;
import java.util.List;
import fu.osms.sync.tiktok.dto.TikTokAuthorizedShop;

public interface TikTokApiClient {
    String executeGet(String apiPath, Map<String, String> queryParams, String accessToken);

    String executePost(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken);

    String executePut(String apiPath, Map<String, String> queryParams, String rawJsonBody, String accessToken);

    String uploadProductImage(String imageUrl, String useCase, String accessToken);

    List<TikTokAuthorizedShop> getAuthorizedShops(String accessToken);
}
