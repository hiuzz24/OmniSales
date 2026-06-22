package fu.osms.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItemResponse {
    private UUID id;
    private UUID warehouseId;
    private String warehouseName;
    private UUID variantId;
    private String variantSku;
    private String variantName;
    private Integer quantityOnHand;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private BigDecimal averageCost;
    private Integer lowStockThreshold;
    private Boolean isLowStock;
    private OffsetDateTime updatedAt;
}
