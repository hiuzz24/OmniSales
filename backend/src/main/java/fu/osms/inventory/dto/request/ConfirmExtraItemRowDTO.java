package fu.osms.inventory.dto.request;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfirmExtraItemRowDTO {
    private Integer rowIndex;
    private String rawInputName;
    private UUID variantId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private boolean skipped;
}
