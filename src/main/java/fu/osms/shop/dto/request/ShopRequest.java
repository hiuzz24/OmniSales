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

    @NotBlank(message = "Shop name must not be blank")
    @Size(max = 255)
    private String name;

    @NotBlank(message = "Slug must not be blank")
    @Size(max = 100)
    private String slug;

    private Boolean isActive = true;
}
