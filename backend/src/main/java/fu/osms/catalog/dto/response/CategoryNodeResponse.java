package fu.osms.catalog.dto.response;

import lombok.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryNodeResponse {
    private UUID id;
    private String name;
    private String slug;
    private Integer sortOrder;

    @Builder.Default
    private List<CategoryNodeResponse> children = new ArrayList<>();
}