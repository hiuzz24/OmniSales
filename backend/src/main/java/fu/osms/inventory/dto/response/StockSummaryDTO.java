package fu.osms.inventory.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockSummaryDTO {
    private Integer quantityOnHand;
    private Integer availableQuantity;
    private Integer reservedQuantity;
}
