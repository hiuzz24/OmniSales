package fu.osms.inventory.dto.request;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeliveryFromReceiptRequest {

    private String recipient;

    private String note;

    /**
     * Optional quantity overrides for receipt items. When null or empty,
     * the delivery is created with the full quantity of every receipt item.
     */
    @Valid
    private List<StockDeliveryFromReceiptItemRequest> items;
}
