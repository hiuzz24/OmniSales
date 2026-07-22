package fu.osms.sync.order.importing;

import fu.osms.order.entity.Order;

public record OrderUpsertResult(Order order, boolean created) {
}
