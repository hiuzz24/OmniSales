package fu.osms.sync.service;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;

import java.util.List;

public interface LazadaPayloadBuilder {
    /**
     * Build the XML payload string for creating or updating a product on Lazada.
     *
     * @param product          The product entity
     * @param variants         List of active product variants
     * @param lazadaImageUrls  List of migrated Lazada CDN image URLs corresponding to the product
     * @return The XML payload string
     */
    String buildPayload(Product product, List<ProductVariant> variants, List<String> lazadaImageUrls);
}
