package fu.osms.inventory.dto.response;

import fu.osms.inventory.enums.InvTxnType;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionDTO {

    private UUID id;
    private UUID warehouseId;
    private String warehouseName;
    private UUID variantId;
    private String variantSku;
    private String variantName;
    private InvTxnType type;
    private String typeLabel;
    private String referenceType;
    private UUID referenceId;
    private Integer quantityChange;
    private Integer quantityBefore;
    private Integer quantityAfter;
    private BigDecimal unitCost;
    private BigDecimal transactionValue;
    private String note;

    private UUID performedById;
    private String performedByName;
    private OffsetDateTime performedAt;
}
