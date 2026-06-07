package fu.osms.catalog.dto.request;

import fu.osms.catalog.enums.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductRequest {

    @NotNull(message = "Shop ID must not be null")
    private UUID shopId;

    private UUID categoryId;

    @Size(max = 100)
    private String sku;

    @NotBlank(message = "Product name must not be blank")
    @Size(max = 500)
    private String name;

    private String description;

    @Size(max = 255)
    private String brand;

    private ProductStatus status = ProductStatus.DRAFT;

    private Integer lowStockThreshold = 5;

    private Integer weightGrams;

    private Map<String, Object> attributes;

    private List<ProductVariantRequest> variants;

    private List<ProductImageRequest> images;
}
