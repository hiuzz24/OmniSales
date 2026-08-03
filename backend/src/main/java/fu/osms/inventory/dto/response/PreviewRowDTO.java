package fu.osms.inventory.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PreviewRowDTO {
    private Integer rowIndex;
    private String rawInputName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private String matchStatus;
    private UUID matchedVariantId;
    private String matchedProductName;
    private String reason;
    private List<ProductSuggestionDTO> suggestions;
}
