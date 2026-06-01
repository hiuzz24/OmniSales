package fu.osms.catalog.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageResponse {

    private UUID id;
    private UUID productId;
    private UUID variantId;
    private String url;
    private Short sortOrder;
    private Boolean isPrimary;
    private OffsetDateTime createdAt;
}
