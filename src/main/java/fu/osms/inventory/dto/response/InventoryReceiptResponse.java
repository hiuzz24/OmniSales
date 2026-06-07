package fu.osms.inventory.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReceiptResponse {

    private UUID id;
    private UUID shopId;
    private UUID warehouseId;
    private String warehouseName;
    private UUID supplierId;
    private String supplierName;
    private String receiptCode;
    private String invoiceNumber;
    private String status;
    private BigDecimal totalCost;
    private OffsetDateTime receivedAt;
    private String notes;
    private UUID createdById;
    private String createdByName;
    private UUID approvedById;
    private String approvedByName;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<InventoryReceiptItemResponse> items;
}
