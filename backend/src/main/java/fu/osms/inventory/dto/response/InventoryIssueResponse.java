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
public class InventoryIssueResponse {

    private UUID id;

    private UUID warehouseId;
    private String warehouseName;
    private String issueCode;
    private String issueType;
    private String status;
    private UUID referenceId;
    private BigDecimal totalCost;
    private String notes;
    private UUID createdById;
    private String createdByName;
    private UUID approvedById;
    private String approvedByName;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<InventoryIssueItemResponse> items;
}
