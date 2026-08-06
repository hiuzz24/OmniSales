package fu.osms.inventory.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeSessionResponse {

    private UUID id;

    private UUID warehouseId;
    private String warehouseName;
    private String warehouseAddress;
    private String sessionCode;
    private LocalDate scheduledDate;
    private String status;
    private UUID createdById;
    private String createdByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private UUID startedById;
    private String startedByName;
    private OffsetDateTime startedAt;
    private UUID completedById;
    private String completedByName;
    private OffsetDateTime completedAt;
    private UUID cancelledById;
    private String cancelledByName;
    private OffsetDateTime cancelledAt;

    private String notes;

    private Integer totalItems;
    private Integer checkedCount;
    private Integer matchedCount;
    private Integer surplusCount;
    private Integer shortageCount;
    private Integer totalSystemQuantity;
    private Integer totalActualQuantity;
    private Integer totalDifference;
    private java.math.BigDecimal totalDifferenceValue;

    private List<StocktakeItemResponse> items;
}
