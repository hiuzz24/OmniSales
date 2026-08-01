package fu.osms.sync.tiktok.returning;

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

@Component
public class TikTokReturnSnapshotMapper {

    public String externalReturnId(Map<String, Object> source) {
        Map<String, Object> data = data(source);
        return text(data, "return_id", "returnId", "reverse_order_id", "id");
    }

    public OrderReturnSnapshot map(Map<String, Object> source, String webhookEventId) {
        Map<String, Object> data = data(source);
        String status = text(data, "return_status", "returnStatus", "status");
        List<OrderReturnSnapshot.Item> items = items(data).stream().map(item -> {
            int quantity = integer(first(item, "return_quantity", "quantity", "requested_quantity"), 1);
            return new OrderReturnSnapshot.Item(
                    text(item, "return_line_item_id", "return_item_id", "id"),
                    text(item, "order_line_item_id", "line_item_id", "order_item_id"),
                    text(item, "seller_sku", "sku_id", "sku"),
                    fallback(text(item, "product_name", "name"), "TikTok return item"),
                    quantity,
                    quantity,
                    isCompleted(status) ? quantity : null);
        }).toList();
        return new OrderReturnSnapshot(
                externalReturnId(data),
                text(data, "order_id", "orderId", "trade_order_id"),
                status,
                mapStatus(status),
                date(first(data, "update_time", "updated_at", "updatedAt")),
                webhookEventId,
                isRefundOnly(data),
                isCompleted(status),
                items,
                Map.of());
    }

    public boolean isPhysicalReturn(Map<String, Object> source) {
        Map<String, Object> data = data(source);
        String reverseEventType = text(data, "reverse_event_type", "reverseEventType");
        if ("ORDER_RETURN".equalsIgnoreCase(reverseEventType)) {
            return true;
        }
        String type = text(data, "return_type", "request_type", "reverse_type", "type");
        String status = text(data, "return_status", "returnStatus", "status");
        return contains(type, "RETURN")
                || contains(status, "RETURN")
                || data.containsKey("return_id")
                || ("3".equals(type) && data.containsKey("reverse_order_id"));
    }

    private OrderReturnStatus mapStatus(String status) {
        String normalized = normalize(status);
        return switch (normalized) {
            case "RETURN_OR_REFUND_REQUEST_PENDING" -> OrderReturnStatus.PENDING_APPROVAL;
            case "AWAITING_BUYER_SHIP" -> OrderReturnStatus.AWAITING_RETURN;
            case "BUYER_SHIPPED_ITEM" -> OrderReturnStatus.RETURN_IN_TRANSIT;
            case "REQUEST_SUCCESS", "RETURN_OR_REFUND_REQUEST_COMPLETE" ->
                    OrderReturnStatus.PLATFORM_PROCESSING;
            case "REQUEST_REJECTED", "RECEIVE_REJECTED", "RETURN_OR_REFUND_CANCEL" ->
                    OrderReturnStatus.REJECTED;
            default -> {
                if (contains(normalized, "REJECT") || contains(normalized, "CANCEL")) {
                    yield OrderReturnStatus.REJECTED;
                }
                yield OrderReturnStatus.PENDING_APPROVAL;
            }
        };
    }

    private boolean isRefundOnly(Map<String, Object> source) {
        String type = text(source, "return_type", "request_type", "reverse_type");
        return contains(type, "REFUND") && !contains(type, "RETURN");
    }

    private boolean isCompleted(String status) {
        return "RETURN_OR_REFUND_REQUEST_COMPLETE".equals(normalize(status));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> data(Map<String, Object> source) {
        Map<String, Object> data = WebhookPayloadUtils.copyMap(source.get("data"));
        return data.isEmpty() ? source : data;
    }

    private List<Map<String, Object>> items(Map<String, Object> source) {
        Object value = first(source, "return_line_items", "return_items", "items", "line_items");
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
