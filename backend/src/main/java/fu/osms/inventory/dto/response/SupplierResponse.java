package fu.osms.inventory.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupplierResponse {

    private UUID id;
    private UUID shopId;
    private String name;
    private String contactName;
    private String phone;
    private String email;
    private String address;
    private Boolean isActive;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
