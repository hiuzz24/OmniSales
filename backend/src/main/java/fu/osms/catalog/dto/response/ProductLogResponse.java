package fu.osms.catalog.dto.response;

import fu.osms.catalog.enums.ProductLogAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductLogResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productSku;
    private UUID variantId;
    private String variantSku;
    private String sku;
    private ProductLogAction action;
    private Map<String, Object> fieldChanges;
    private String performedByEmail;
    private String referenceType;
    private UUID referenceId;
    private String notes;
    private OffsetDateTime performedAt;
}
