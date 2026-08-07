package fu.osms.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDeliveryItemResponse {

    private UUID id;
    private UUID productVariantId;
    private String productVariantName;
    private String productName;
    private String sku;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String note;
    private Boolean isGift;
}
