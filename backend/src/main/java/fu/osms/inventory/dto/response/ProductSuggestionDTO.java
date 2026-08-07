package fu.osms.inventory.dto.response;

import lombok.*;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductSuggestionDTO {
    private UUID variantId;
    private String productName;
    private Double score;
}
