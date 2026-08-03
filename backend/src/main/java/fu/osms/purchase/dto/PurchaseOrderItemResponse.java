package fu.osms.purchase.dto;

import fu.osms.common.enums.PlatformType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PurchaseOrderItemResponse {
    private UUID id;
    private UUID variantId;
    private String sku;
    private String marketplaceSku;
    private String productName;
    private String variantName;
    private List<PlatformType> platforms;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private BigDecimal salePrice;
}
