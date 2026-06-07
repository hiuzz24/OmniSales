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
    private Integer systemQuantity;
    private Integer actualQuantity;
    private Integer difference;
    private String notes;
}
