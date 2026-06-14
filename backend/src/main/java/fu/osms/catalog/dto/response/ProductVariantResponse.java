package fu.osms.catalog.dto.response;

import fu.osms.inventory.dto.response.InventoryItemResponse;
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
public class ProductVariantResponse {

    private UUID id;
    private UUID productId;
    private String productName;

    private String sku;
    private String name;
    private String barcode;
    private java.math.BigDecimal price;
    private java.math.BigDecimal costPrice;
    private Boolean isActive;
    private Map<String, Object> optionValues;
    private Integer weightGrams;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private Integer availableQuantity;
    private Integer quantityOnHand;

    private List<ProductImageResponse> images;
}
