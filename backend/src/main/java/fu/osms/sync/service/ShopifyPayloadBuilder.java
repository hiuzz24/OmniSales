package fu.osms.sync.service;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductImage;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.sync.dto.shopify.request.ShopifyProductPayload;

import java.util.List;

public interface ShopifyPayloadBuilder {
    ShopifyProductPayload buildPayload(Product product, List<ProductVariant> variants, List<ProductImage> images);
}
