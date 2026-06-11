package fu.osms.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryIssueItemResponse {

    private UUID id;
    private UUID variantId;
    private String variantSku;
    private String variantName;
    private Integer quantity;
    private BigDecimal unitCost;
    private BigDecimal totalCost;
    private String notes;
}
