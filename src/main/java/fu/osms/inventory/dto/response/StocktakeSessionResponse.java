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
    private UUID shopId;
    private UUID warehouseId;
    private String warehouseName;
    private String sessionCode;
    private LocalDate scheduledDate;
    private String status;
    private UUID createdById;
    private String createdByName;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private List<StocktakeItemResponse> items;
}
