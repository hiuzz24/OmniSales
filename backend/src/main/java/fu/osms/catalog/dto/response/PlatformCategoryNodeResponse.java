package fu.osms.catalog.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class PlatformCategoryNodeResponse {
    private String id;
    private String parentId;
    private String name;
    private Boolean leaf;
    private Boolean available;
}
