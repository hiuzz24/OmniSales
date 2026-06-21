package fu.osms.inventory.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockDeliveryResponse {

    private UUID id;
    private String issueCode;
    private UUID warehouseId;
    private String warehouseName;
    private UUID orderId;
    private String deliveryType;
    private String issueType;
    private String issueTypeLabel;
    private String recipient;
    private OffsetDateTime issuedAt;
    private String note;
    private Integer totalSkuCount;
    private Integer totalQuantity;
    private BigDecimal totalCost;
    private String status;
    private UUID createdBy;
    private String createdByName;
    private UUID approvedBy;
    private String approvedByName;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<StockDeliveryItemResponse> items;
}
