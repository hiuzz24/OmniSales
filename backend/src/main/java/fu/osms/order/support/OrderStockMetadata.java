package fu.osms.order.support;

import fu.osms.order.entity.Order;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OrderStockMetadata {
    private OrderStockMetadata() {}

    public static void markPlatformConflict(Order order, String platformStatus) {
        Map<String, Object> metadata = copy(order);
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("active", true);
        conflict.put("platformStatus", platformStatus);
        conflict.put("detectedAt", OffsetDateTime.now().toString());
        metadata.put("platformProgressConflict", conflict);
        order.setPlatformMetadata(metadata);
    }

    public static void clearLifecycle(Order order) {
        order.setWaitingStockAt(null);
        order.setWaitingStockExpiresAt(null);
        order.setWaitingStockExpiryNotifiedAt(null);
        Map<String, Object> metadata = copy(order);
        metadata.remove("waitingStock");
        metadata.remove("platformProgressConflict");
        order.setPlatformMetadata(metadata);
    }

    private static Map<String, Object> copy(Order order) {
        return new LinkedHashMap<>(order.getPlatformMetadata() == null ? Map.of() : order.getPlatformMetadata());
    }
}
