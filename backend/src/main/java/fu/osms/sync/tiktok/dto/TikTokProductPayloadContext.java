package fu.osms.sync.tiktok.dto;

import fu.osms.catalog.dto.response.PlatformAttributeResponse;
import fu.osms.catalog.entity.Product;
import fu.osms.catalog.entity.ProductVariant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record TikTokProductPayloadContext(
        Product product,
        List<ProductVariant> variants,
        Map<String, Object> config,
        List<PlatformAttributeResponse> attributeSchema,
        List<String> imageUris,
        String sizeChartImageUri,
        Map<UUID, String> externalVariantIdByVariantId,
        boolean create,
        String defaultWarehouseId,
        String currency
) {
}
