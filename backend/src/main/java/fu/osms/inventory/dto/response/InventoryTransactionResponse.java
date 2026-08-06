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
public class InventoryTransactionResponse {

    private UUID id;

    private UUID warehouseId;
    private String warehouseName;
    private UUID variantId;
    private String variantSku;
    private String productName;
    private String variantName;
    private InvTxnType type;
    private String referenceType;
    private UUID referenceId;
    private Integer quantityChange;
    private Integer quantityBefore;
    private Integer quantityAfter;
    /** Unit cost at time of transaction — used to calculate transaction value. */
    private BigDecimal unitCost;
    /**
     * Average cost per unit AFTER this transaction was applied.
     * Present for IMPORT transactions linked to a receipt item.
     * Used as fallback when unitCost is null (e.g. ADJUSTMENT, TRANSFER).
     */
    private BigDecimal avgCostAfter;
    /**
     * Pre-computed transaction value = |quantityChange| × effectiveCost.
     * Null when no cost data is available.
     */
    private BigDecimal transactionValue;
    private String note;
    private UUID performedById;
    private String performedByName;
    private OffsetDateTime performedAt;
}
