package fu.osms.catalog.dto.response;

import fu.osms.catalog.enums.ProductStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductResponse {

    private UUID id;
    private UUID shopId;
    private String shopName;
    private UUID categoryId;
    private String categoryName;
    private String sku;
    private String name;
    private String description;
    private String brand;
    private ProductStatus status;
    private Integer lowStockThreshold;
    private Integer weightGrams;
    private Map<String, Object> attributes;
    private Long version;
    private UUID createdById;
    private String createdByName;
    private UUID updatedById;
    private String updatedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<ProductVariantResponse> variants;
    private List<ProductImageResponse> images;
}
