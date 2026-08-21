package fu.osms.order.support;

import fu.osms.order.entity.Order;
import fu.osms.sync.webhook.WebhookPayloadUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TikTokBuyerCancellationMetadata {
    public static final String PENDING = "CANCELLATION_REQUEST_PENDING";
    public static final String SUCCESS = "CANCELLATION_REQUEST_SUCCESS";
    public static final String COMPLETE = "CANCELLATION_REQUEST_COMPLETE";

    private TikTokBuyerCancellationMetadata() {
    }

    public static boolean isActive(Order order) {
        return booleanValue(value(order).get("active"));
    }

    public static Map<String, Object> value(Order order) {
        if (order == null || order.getPlatformMetadata() == null) return Map.of();
        Map<String, Object> tikTok = WebhookPayloadUtils.copyMap(order.getPlatformMetadata().get("tiktok"));
        return WebhookPayloadUtils.copyMap(tikTok.get("buyerCancellation"));
    }

    public static void replace(Order order, Map<String, Object> cancellation) {
        Map<String, Object> root = new LinkedHashMap<>(
                order.getPlatformMetadata() == null ? Map.of() : order.getPlatformMetadata());
        Map<String, Object> tikTok = new LinkedHashMap<>(WebhookPayloadUtils.copyMap(root.get("tiktok")));
        tikTok.put("buyerCancellation", new LinkedHashMap<>(cancellation));
        root.put("tiktok", tikTok);
        order.setPlatformMetadata(root);
    }

    public static boolean isClosedStatus(String status) {
        return COMPLETE.equalsIgnoreCase(status)
                || "CANCELLATION_REQUEST_CANCEL".equalsIgnoreCase(status)
                || "CANCELLATION_REQUEST_CANCELLED".equalsIgnoreCase(status);
    }

    private static boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
}
