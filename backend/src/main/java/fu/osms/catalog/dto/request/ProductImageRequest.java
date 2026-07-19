package fu.osms.catalog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "(?i)^https?://.+", message = "Image URL must start with http:// or https://")
    private String url;

    @Builder.Default
    private Short sortOrder = 0;

    @Builder.Default
    private Boolean isPrimary = false;
}
