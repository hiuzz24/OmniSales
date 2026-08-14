package fu.osms.inventory.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventorySummaryResponse {

    private long totalProducts;
    private long totalSkus;
    private long totalQuantity;
    private long lowStockSkus;
    private long outOfStockSkus;
    private long negativeStockSkus;
}
