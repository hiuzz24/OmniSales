package fu.osms.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryDetailDTO {
    private Integer quantityOnHand;      // quantity_on_hand
    private BigDecimal averageCost;      // average_cost
    private BigDecimal totalInventoryValue; // quantity_on_hand * average_cost
    private Double profitMargin;         // Tỷ lệ % biên lợi nhuận
    // Khối Thông tin sản phẩm
    private String variantSku;           // variant.sku
    private String productVariantName;        // ten của product variant
    private String categoryName;         // category.name
    private BigDecimal price;            // variant.price
    private OffsetDateTime lastImportedAt;
    private OffsetDateTime lastUpdatedAt;
    // Khối mức tồn kho
    private Integer lowStockThreshold;        // reorder_level
    private String stockStatus;          // Tự tính toán: "Còn hàng", "Sắp hết", "Hết hàng"
    private String warehouseName;        // warehouse.name
}
