package fu.osms.inventory.dto.response;

import fu.osms.common.enums.PlatformType;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
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
    private String marketplaceSku;
    private String variantSku;
    private String variantName;
    private List<PlatformType> platforms;
    private Integer quantity;
    private Integer surplusQuantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private BigDecimal avgCostBefore;
    private BigDecimal avgCostAfter;
    private String notes;
}
