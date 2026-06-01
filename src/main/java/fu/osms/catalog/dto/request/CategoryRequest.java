package fu.osms.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryRequest {

    @NotNull(message = "Shop ID không được để trống")
    private UUID shopId;

    private UUID parentId;

    @NotBlank(message = "Tên danh mục không được để trống")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 255)
    private String slug;

    private Integer sortOrder = 0;
}
