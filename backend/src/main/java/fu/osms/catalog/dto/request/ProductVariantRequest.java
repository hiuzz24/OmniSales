package fu.osms.catalog.dto.request;

import jakarta.validation.Valid;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariantRequest {

    private UUID id;

    private String sku;

    private String name;

    private String barcode;

    private BigDecimal price;

    private BigDecimal costPrice;

    @Builder.Default
    private Boolean isActive = true;

    private Map<String, Object> optionValues;

    private Integer weightGrams;

    @Valid
    private List<ProductImageRequest> images;
}
