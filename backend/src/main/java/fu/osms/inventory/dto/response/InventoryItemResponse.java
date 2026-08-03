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
    private UUID productId;
    private List<UUID> productIds;
    private UUID variantId;
    // All variant IDs grouped under this SKU (merged across channels).
    // Used by the frontend to reliably match Excel import rows back to the correct variant.
    private List<UUID> variantIds;
    private String variantSku;
    // The raw internal SKU from ProductVariant.sku — always the value used in the Excel
    // template display name format "{productName} - {variantName} [{internalVariantSku}]".
    // variantSku may be overridden with the marketplace externalSku; this field is never overridden.
    private String internalVariantSku;
    private String marketplaceSku;
    private String productName;
    private String variantName;
    private UUID categoryId;
    private String categoryName;
    private UUID channelId;
    private String channelName;
    private PlatformType platform;
    private List<UUID> channelIds;
    private List<String> channelNames;
    private List<PlatformType> platforms;
    private BigDecimal unitPrice;
    private BigDecimal salePrice;
    private BigDecimal currentSalePrice;
    private Integer mergedInventoryItemCount;
    private Integer mergedVariantCount;
    private Integer quantityOnHand;
    private Integer reservedQuantity;
    private Integer availableQuantity;
    private Integer incomingQuantity;
    private Integer outgoingQuantity;
    private BigDecimal averageCost;
    private BigDecimal price;
    private Integer lowStockThreshold;
    private Boolean isLowStock;
    private OffsetDateTime updatedAt;
}
