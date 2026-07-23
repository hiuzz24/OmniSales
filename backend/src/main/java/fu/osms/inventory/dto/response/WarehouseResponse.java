package fu.osms.inventory.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseResponse {

    private UUID id;

    private String name;
    private String address;
    private Boolean isActive;
    private Integer staffCount;
    private Integer productCount;
    private Integer totalStock;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
