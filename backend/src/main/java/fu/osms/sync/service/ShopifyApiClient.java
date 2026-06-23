package fu.osms.sync.service;

import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;
import fu.osms.sync.dto.shopify.response.ShopifyProductResponse;

public interface ShopifyApiClient {
    ShopifyProductResponse createProduct(String shopDomain, String accessToken, ShopifyProductPayload payload);
    ShopifyProductResponse updateProduct(String shopDomain, String accessToken, String externalProductId, ShopifyProductPayload payload);
}
