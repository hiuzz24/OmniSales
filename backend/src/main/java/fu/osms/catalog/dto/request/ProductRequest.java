package fu.osms.catalog.dto.request;

import fu.osms.catalog.enums.ProductStatus;
import fu.osms.catalog.validation.ValidProductRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
@ValidProductRequest
public class ProductRequest {

    @NotNull(message = "Product category must not be null")
    private UUID categoryId;

    private UUID warehouseId;

    @NotBlank(message = "Product SKU must not be blank")
    @Size(max = 100)
    private String sku;

    @NotBlank(message = "Product name must not be blank")
    @Size(max = 500)
    private String name;

    @NotBlank(message = "Mô tả sản phẩm không được để trống")
    @Size(max = 5000, message = "Mô tả sản phẩm tối đa 5000 ký tự")
    private String description;

    @NotBlank(message = "Thương hiệu không được để trống")
    @Size(max = 255)
    private String brand;

    @NotBlank(message = "Đơn vị tính không được để trống")
    @Size(max = 50)
    private String unit;

    @NotNull(message = "Loại sản phẩm không được để trống")
    private Boolean hasVariants;

    @Builder.Default
    private ProductStatus status = ProductStatus.DRAFT;

    @Builder.Default
    @NotNull(message = "Ngưỡng tồn kho không được để trống")
    @PositiveOrZero(message = "Ngưỡng tồn kho phải là số không âm")
    private Integer lowStockThreshold = 5;

    @NotNull(message = "Khối lượng đóng gói không được để trống")
    @Positive(message = "Khối lượng đóng gói phải lớn hơn 0")
    private Integer weightGrams;

    private Map<String, Object> attributes;

    @NotEmpty(message = "Sản phẩm phải có ít nhất một biến thể")
    @Valid
    private List<ProductVariantRequest> variants;

    private Long version;

    @NotEmpty(message = "Product must have at least one image")
    @Valid
    private List<ProductImageRequest> images;

    private List<UUID> channelIds;

    @Valid
    private List<ChannelConfigRequest> channelConfigs;
}
