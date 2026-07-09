package fu.osms.sync.shopify;

import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;
import fu.osms.sync.dto.shopify.response.ShopifyWebhookResponse;

import java.util.List;

public interface ShopifyApiClient {
    ShopifyProductResponse createProduct(String shopDomain, String accessToken, ShopifyProductPayload payload);
    ShopifyProductResponse updateProduct(String shopDomain, String accessToken, String externalProductId, ShopifyProductPayload payload);

    ShopifyWebhookResponse createWebhook(String shopDomain, String accessToken, String topic, String callbackUrl);

    List<ShopifyWebhookResponse> listWebhooks(String shopDomain, String accessToken);

    void deleteWebhook(String shopDomain, String accessToken, Long webhookId);

    List<Map<String, Object>> getFulfillmentOrders(String shopDomain, String accessToken, String orderId);

    Map<String, Object> createFulfillment(String shopDomain, String accessToken, String fulfillmentOrderId, String trackingNumber);

    Map<String, Object> cancelOrder(String shopDomain, String accessToken, String orderId,
                                    ShopifyCancelReason reason, boolean email, boolean restock, boolean refund);

    List<String> listAccessScopes(String shopDomain, String accessToken);

    Map<String, Object> executeGraphQl(String shopDomain, String accessToken, String query, Map<String, Object> variables);
}
