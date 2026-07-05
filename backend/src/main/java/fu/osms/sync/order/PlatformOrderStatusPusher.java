package fu.osms.sync.order;

import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;

public interface PlatformOrderStatusPusher {

    PlatformType getPlatform();

    OrderStatusPushResult push(Order order, OrderStatus targetStatus, String cancelReason);
}
