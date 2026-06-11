package fu.osms.catalog.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {

    private UUID id;

    private UUID parentId;
    private String parentName;
    private String name;
    private String slug;
    private Integer sortOrder;
    private OffsetDateTime createdAt;
}
