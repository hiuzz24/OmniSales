package fu.osms.shop.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopRequest {

    @NotBlank(message = "Tên shop không được để trống")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Slug không được để trống")
    @Size(max = 100)
    private String slug;

    private Boolean isActive = true;
}
