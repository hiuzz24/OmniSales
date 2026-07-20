package fu.osms.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import fu.osms.common.enums.PlatformType;

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
    private String productName;
    private String variantName;
    private UUID channelId;
    private String channelName;
    private PlatformType platform;
    private List<UUID> channelIds;
    private List<String> channelNames;
    private List<PlatformType> platforms;
    private Integer mergedInventoryItemCount;
    private Integer mergedVariantCount;
    private Integer quantityOnHand;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private BigDecimal averageCost;
    private Integer lowStockThreshold;
    private Boolean isLowStock;
    private OffsetDateTime updatedAt;
}
