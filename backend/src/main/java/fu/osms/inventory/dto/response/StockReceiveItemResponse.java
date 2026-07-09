package fu.osms.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockReceiveItemResponse {

    private UUID id;
    private UUID variantId;
    private String productName;
    private String sku;
    private String variantSku;
    private String variantName;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private BigDecimal avgCostBefore;
    private BigDecimal avgCostAfter;
    private String notes;
}
