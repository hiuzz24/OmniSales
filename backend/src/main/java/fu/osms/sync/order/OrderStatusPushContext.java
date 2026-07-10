package fu.osms.sync.order;

import fu.osms.order.enums.ShopifyCancelReason;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderStatusPushContext {

    private final String cancelReason;
    private final String cancelReasonId;
    private final ShopifyCancelReason shopifyReason;
    private final Boolean email;
    private final Boolean restock;
    private final Boolean refund;

    public static OrderStatusPushContext empty() {
        return OrderStatusPushContext.builder().build();
    }
}
