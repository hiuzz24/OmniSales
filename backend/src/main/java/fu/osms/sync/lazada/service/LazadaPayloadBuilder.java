package fu.osms.sync.lazada.service;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;

import java.util.List;

public interface LazadaPayloadBuilder {
    String buildPayload(Product product, List<ProductVariant> variants, List<String> lazadaImageUrls);
}
