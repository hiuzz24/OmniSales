package fu.osms.sync.lazada.returning;

import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashMap;

@Component
public class LazadaReturnSnapshotMapper {

    public OrderReturnSnapshot map(Map<String, Object> source, String webhookEventId) {
        Map<String, Object> data = data(source);
        String status = text(data, "reverse_status", "reverseStatus", "status");
        List<OrderReturnSnapshot.Item> items = items(data).stream()
                .map(item -> {
                    int quantity = integer(first(item, "quantity", "return_quantity", "returnQuantity"), 1);
                    Integer refunded = isCompleted(status) ? quantity : null;
                    return new OrderReturnSnapshot.Item(
                            text(item, "reverse_order_line_id", "reverseOrderLineId", "reverse_item_id"),
                            text(item, "trade_order_line_id", "tradeOrderLineId", "order_item_id", "orderItemId"),
                            text(item, "seller_sku", "sku"),
                            fallback(text(item, "name", "product_name"), "Lazada return item"),
                            quantity,
                            quantity,
                            refunded);
                })
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        String returnReason = text(data, "return_reason", "returnReason", "reason", "reason_text", "buyer_reason");
        if (returnReason != null && !returnReason.isBlank()) metadata.put("returnReason", returnReason);
        return new OrderReturnSnapshot(
                text(data, "reverse_order_id", "reverseOrderId", "reverse_id", "id"),
                text(data, "trade_order_id", "tradeOrderId", "order_id", "orderId"),
                status,
                mapStatus(status),
                date(first(data, "updated_at", "update_time", "updatedAt")),
                webhookEventId,
                isRefundOnly(data),
                isCompleted(status),
                items,
                metadata);
    }

    public boolean isPhysicalReturn(Map<String, Object> source) {
        Map<String, Object> data = data(source);
        String type = text(data, "reverse_type", "reverseType", "request_type", "requestType");
        String status = text(data, "reverse_status", "reverseStatus", "status");
        return contains(type, "RETURN") || contains(type, "RTM") || contains(status, "RETURN");
    }

    private OrderReturnStatus mapStatus(String status) {
        if (contains(status, "REJECT") || contains(status, "CANCEL")) return OrderReturnStatus.REJECTED;
        if (isCompleted(status)) return OrderReturnStatus.PLATFORM_PROCESSING;
        if (contains(status, "TRANSIT") || contains(status, "SHIPPING")) return OrderReturnStatus.RETURN_IN_TRANSIT;
        if (contains(status, "APPROV") || contains(status, "WAITING_RETURN")) return OrderReturnStatus.AWAITING_RETURN;
        return OrderReturnStatus.PENDING_APPROVAL;
    }

    private boolean isRefundOnly(Map<String, Object> data) {
        String type = text(data, "reverse_type", "reverseType", "request_type", "requestType");
        return contains(type, "REFUND") && !contains(type, "RETURN") && !contains(type, "RTM");
    }

    private boolean isCompleted(String status) {
        return contains(status, "COMPLETED") || contains(status, "CLOSED") || contains(status, "REFUNDED");
    }

    private Map<String, Object> data(Map<String, Object> source) {
        Map<String, Object> data = WebhookPayloadUtils.copyMap(source.get("data"));
        return data.isEmpty() ? source : data;
    }

    private List<Map<String, Object>> items(Map<String, Object> source) {
        Object value = first(source, "reverse_order_lines", "reverseOrderLines", "items", "order_items");
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(WebhookPayloadUtils::copyMap).toList();
    }

    private OffsetDateTime date(Object value) {
        if (value == null) return null;
        try {
            String text = value.toString();
            if (text.matches("\\d+")) return Instant.ofEpochSecond(Long.parseLong(text)).atOffset(ZoneOffset.UTC);
            return OffsetDateTime.parse(text);
        } catch (DateTimeParseException | NumberFormatException ignored) {
            return null;
        }
    }

    private Object first(Map<String, Object> source, String... keys) {
        return WebhookPayloadUtils.firstPresent(source, keys);
    }

    private String text(Map<String, Object> source, String... keys) {
        return WebhookPayloadUtils.text(first(source, keys));
    }

    private int integer(Object value, int fallback) {
        return WebhookPayloadUtils.integer(value, fallback);
    }

    private boolean contains(String value, String token) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(token);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
