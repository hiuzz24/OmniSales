package fu.osms.sync.order;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.dto.response.CancelReasonResponse;
import fu.osms.order.enums.OrderStatus;

import java.util.List;

public interface PlatformOrderStatusPusher {

    PlatformType getPlatform();

    OrderStatusPushResult push(Order order, OrderStatus targetStatus, OrderStatusPushContext context);

    default List<CancelReasonResponse> getCancelReasons(Order order) {
        return List.of();
    }
}
