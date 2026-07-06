package fu.osms.inventory.dto.response;

import fu.osms.common.enums.PlatformType;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailableVariantDTO {
    private UUID variantId;
    private String sku;
    private String productName;
    private String variantName;
    private BigDecimal unitPrice;
    private BigDecimal averageCost;
    private Integer availableQuantity;
    private UUID channelId;
    private String channelName;
    private PlatformType platform;
}
