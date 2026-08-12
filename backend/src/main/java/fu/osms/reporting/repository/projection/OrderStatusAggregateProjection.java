package fu.osms.reporting.repository.projection;

import fu.osms.order.enums.OrderStatus;

public interface OrderStatusAggregateProjection {
    OrderStatus getStatus();

    Long getOrderCount();
}
