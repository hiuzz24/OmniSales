package fu.osms.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    @NotBlank(message = "SKU must not be blank")
    @Size(max = 100)
    private String sku;

    @Size(max = 255)
    private String name;

    @Size(max = 100)
    private String barcode;

    @NotNull(message = "Price must not be null")
    @Positive(message = "Price must be greater than 0")
    private BigDecimal price;

    private BigDecimal costPrice;

    @Builder.Default
    private Boolean isActive = true;

    private Map<String, Object> optionValues;

    private Integer weightGrams;

    private List<ProductImageRequest> images;
}
