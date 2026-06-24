package fu.osms.inventory.dto.response;

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
    private String variantName;
    private BigDecimal unitPrice;
    private Integer availableQuantity;
}
