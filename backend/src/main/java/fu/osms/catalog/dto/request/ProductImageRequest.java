package fu.osms.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageRequest {

    @NotBlank(message = "Image URL must not be blank")
    private String url;

    @Builder.Default
    private Short sortOrder = 0;

    @Builder.Default
    private Boolean isPrimary = false;
}
