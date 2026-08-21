package fu.osms.order.event;

import java.util.UUID;

public record OrderPlatformStockConflictEvent(UUID orderId, String platformStatus) {
}
