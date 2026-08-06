package fu.osms.inventory.dto.response;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StocktakeItemResponse {

    private UUID id;
    private UUID variantId;
    private String variantSku;
    private String variantName;
    private UUID productId;
    private String productName;
    private String barcode;
    private String unit;
    private java.math.BigDecimal costPrice;
    private Integer systemQuantity;
    private Integer actualQuantity;
    private Integer difference;
    private java.math.BigDecimal differenceValue;
    private Boolean checked;
    private String notes;
}
