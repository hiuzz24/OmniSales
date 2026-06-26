package fu.osms.inventory.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockTransferResponseDTO {

    private UUID id;
    private String transferCode;
    private String fromWarehouseName;
    private String toWarehouseName;
    private String status;
    private String createdBy;
    private OffsetDateTime createdAt;
    private String note;
    private Integer skuCount;
    private Integer totalQuantity;
    private UUID toWarehouseId;
    private UUID createdById;
}
