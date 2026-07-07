package fu.osms.sync.shopify;

import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;
import fu.osms.sync.dto.shopify.response.ShopifyWebhookResponse;

import java.util.List;
import java.util.Map;

public interface ShopifyApiClient {
    ShopifyProductResponse createProduct(String shopDomain, String accessToken, ShopifyProductPayload payload);
    ShopifyProductResponse updateProduct(String shopDomain, String accessToken, String externalProductId, ShopifyProductPayload payload);

    ShopifyWebhookResponse createWebhook(String shopDomain, String accessToken, String topic, String callbackUrl);

    List<ShopifyWebhookResponse> listWebhooks(String shopDomain, String accessToken);

    void deleteWebhook(String shopDomain, String accessToken, Long webhookId);

    List<String> listAccessScopes(String shopDomain, String accessToken);

    Map<String, Object> executeGraphQl(String shopDomain, String accessToken, String query, Map<String, Object> variables);
}
