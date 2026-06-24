package fu.osms.sync.shopify;

public interface ShopifyOAuthService {
    String buildAuthorizationUrl(String shop);
    String exchangeCodeForToken(String shop, String code);
}
