package fu.osms.order.dto.request;

import fu.osms.order.enums.ShopifyCancelReason;
import lombok.Data;

@Data
public class CancelOrderRequest {

    private String reason;
    private String reasonId;
    private ShopifyCancelReason shopifyReason;
    private Boolean email;
    private Boolean restock;
    private Boolean refund;
}
