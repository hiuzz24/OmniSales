package fu.osms.sync.shopify;

public interface ShopifyShopDomainNormalizer {

    String normalizeHandle(String input);

    String canonicalDomain(String input);
}
