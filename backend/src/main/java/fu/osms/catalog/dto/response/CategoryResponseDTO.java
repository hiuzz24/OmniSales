package fu.osms.catalog.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponseDTO {
    private UUID id;
    private String name;
    private String slug;
    private String parentCategoryName;
    private Integer sortOrder;
    private long productCount;
    private String status;
    private OffsetDateTime createdAt;
}
