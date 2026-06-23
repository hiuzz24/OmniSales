package fu.osms.sync.service;

public interface ShopifyOAuthService {
    String buildAuthorizationUrl(String shop);
    String exchangeCodeForToken(String shop, String code);
}
