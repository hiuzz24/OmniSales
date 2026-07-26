package fu.osms.sync.service;

import fu.osms.catalog.entity.Product;
import fu.osms.channel.entity.ChannelProduct;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class PlatformCatalogOwnershipPolicy {

    public static final String OWNERSHIP_KEY = "catalogOwnership";
    public static final String PLATFORM_IMPORT = "PLATFORM_IMPORT";

    public boolean isPlatformOwned(ChannelProduct mapping, Product product, String externalProductId) {
        if (mapping != null
                && mapping.getMetadata() != null
                && PLATFORM_IMPORT.equals(mapping.getMetadata().get(OWNERSHIP_KEY))) {
            return true;
        }
        if (product == null || !isGeneratedSku(product.getSku()) || !hasText(externalProductId)) {
            return false;
        }
        if (product.getSku().equalsIgnoreCase("EXT-" + externalProductId)) {
            return true;
        }
        Map<String, Object> attributes = product.getAttributes();
        return attributes != null && (
                externalProductId.equals(stringValue(attributes.get("shopifyProductId")))
                        || externalProductId.equals(stringValue(attributes.get("tiktokProductId")))
                        || lazadaProductId(attributes.get("lazadaProduct")).equals(externalProductId)
        );
    }

    public void markPlatformImported(ChannelProduct mapping) {
        Map<String, Object> metadata = mapping.getMetadata() == null
                ? new HashMap<>()
                : new HashMap<>(mapping.getMetadata());
        metadata.put(OWNERSHIP_KEY, PLATFORM_IMPORT);
        mapping.setMetadata(metadata);
    }

    private boolean isGeneratedSku(String sku) {
        return sku != null && (
                sku.startsWith("EXT-")
                        || sku.startsWith("SHOPIFY-")
                        || sku.startsWith("LAZADA-")
                        || sku.startsWith("TIKTOK-")
        );
    }

    @SuppressWarnings("unchecked")
    private String lazadaProductId(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return "";
        }
        Map<String, Object> product = (Map<String, Object>) source;
        Object id = product.get("item_id");
        if (id == null) {
            id = product.get("product_id");
        }
        return stringValue(id);
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
