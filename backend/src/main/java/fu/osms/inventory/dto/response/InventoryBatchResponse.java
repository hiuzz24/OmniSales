package fu.osms.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryBatchResponse {

    private UUID id;
    private UUID shopId;
    private UUID warehouseId;
    private String warehouseName;
    private UUID variantId;
    private String variantSku;
    private String variantName;
    private String batchCode;
    private BigDecimal unitCost;
    private Integer quantityReceived;
    private Integer quantityRemaining;
    private String status;
    private LocalDate expiryDate;
    private OffsetDateTime receivedAt;
    private OffsetDateTime createdAt;
}
