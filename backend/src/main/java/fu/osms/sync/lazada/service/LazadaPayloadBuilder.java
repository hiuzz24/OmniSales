package fu.osms.sync.lazada.service;

import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.sync.lazada.dto.LazadaMigratedImages;
import fu.osms.sync.lazada.dto.LazadaProductConfig;

import java.util.List;
import java.util.Map;

public interface LazadaPayloadBuilder {
    String buildPayload(Product product,
                        List<ProductVariant> variants,
                        LazadaMigratedImages migratedImages,
                        Map<String, String> externalSkuIdBySku,
                        LazadaProductConfig config,
                        boolean isCreate);
}
