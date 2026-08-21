package fu.osms.order.service;

import fu.osms.order.entity.Order;

import java.util.UUID;

public interface OrderStockAllocationService {
    Order classifyAfterImport(UUID orderId);
    Order confirmOrder(UUID orderId);
    Order movePendingOrderToWaitingIfUnavailable(UUID orderId);
    Order reconcileWaitingOrder(UUID orderId);
}
