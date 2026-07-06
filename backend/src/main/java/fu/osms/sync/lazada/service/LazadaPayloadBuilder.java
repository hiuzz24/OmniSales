package fu.osms.sync.lazada.service;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;

import java.util.List;
import java.util.Map;

public interface LazadaPayloadBuilder {
    String buildPayload(Product product,
                        List<ProductVariant> variants,
                        List<String> lazadaImageUrls,
                        Map<String, String> externalSkuIdBySku,
                        boolean includePrimaryCategory);
}
