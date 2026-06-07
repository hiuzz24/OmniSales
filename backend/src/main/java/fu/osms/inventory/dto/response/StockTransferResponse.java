package fu.osms.inventory.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponse {

    private UUID id;
    private UUID shopId;
    private UUID fromWarehouseId;
    private String fromWarehouseName;
    private UUID toWarehouseId;
    private String toWarehouseName;
    private String transferCode;
    private String status;
    private UUID createdById;
    private String createdByName;
    private UUID approvedById;
    private String approvedByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<StockTransferItemResponse> items;
}
