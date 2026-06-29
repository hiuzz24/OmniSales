package fu.osms.catalog.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDashboardResponse {
    private long totalCategories;
    private long totalActiveCategories;
    private long totalParentCategories;
    private long totalProducts;

    private List<CategoryResponseDTO> categories;
    private int totalPages;
    private long totalElements;
}
