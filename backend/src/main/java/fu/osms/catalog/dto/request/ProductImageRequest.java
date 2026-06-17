package fu.osms.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductImageRequest {

    private UUID id;

    @NotBlank(message = "Image URL must not be blank")
    private String url;

    @Builder.Default
    private Short sortOrder = 0;

    @Builder.Default
    private Boolean isPrimary = false;
}
