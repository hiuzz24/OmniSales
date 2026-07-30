package fu.osms.sync.shopify.returning;

import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.model.OrderReturnSnapshot;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
public class ShopifyReturnSnapshotMapper {

    public OrderReturnSnapshot map(Map<String, Object> source, String webhookEventId, boolean refundConfirmedEvent) {
        String externalReturnId = text(source, "id", "admin_graphql_api_id", "return_id");
        Map<String, Object> order = object(source.get("order"));
        String externalOrderId = firstText(order, "legacyResourceId", "legacy_resource_id", "id");
        if (externalOrderId != null && externalOrderId.startsWith("gid://")) {
            externalOrderId = gidTail(externalOrderId);
        }
        String status = firstText(source, "status");
        List<OrderReturnSnapshot.Item> items = returnItems(source).stream().map(item -> {
            Map<String, Object> fulfillment = object(item.get("fulfillmentLineItem"));
            Map<String, Object> line = object(fulfillment.get("lineItem"));
            String orderItemId = firstText(line, "id", "legacyResourceId");
            if (orderItemId != null && orderItemId.startsWith("gid://")) orderItemId = gidTail(orderItemId);
            int quantity = integer(item.get("quantity"), 0);
            int refundedQuantity = integer(item.get("refundedQuantity"), 0);
            Integer confirmedRefundedQuantity = null;
            if (refundedQuantity > 0) {
                confirmedRefundedQuantity = Math.min(refundedQuantity, quantity);
            } else if (refundConfirmedEvent) {
                confirmedRefundedQuantity = quantity;
            }
            return new OrderReturnSnapshot.Item(
                    firstText(item, "id"),
                    orderItemId,
                    firstText(line, "sku"),
                    fallback(firstText(line, "name"), "Shopify return item"),
                    quantity,
                    quantity,
                    confirmedRefundedQuantity);
        }).toList();
        boolean refundConfirmed = refundConfirmedEvent || (!items.isEmpty() && items.stream().allMatch(item ->
                item.refundedQuantity() != null
                        && item.refundedQuantity() >= item.approvedQuantity()));
        return new OrderReturnSnapshot(
                externalReturnId,
                externalOrderId,
                status,
                mapStatus(status),
                date(firstText(source,
                        "closedAt", "closed_at",
                        "requestApprovedAt", "request_approved_at",
                        "createdAt", "created_at")),
                webhookEventId,
                false,
                refundConfirmed,
                items,
                Map.of("shopifyReturnName", fallback(firstText(source, "name"), "")));
    }

    private List<Map<String, Object>> returnItems(Map<String, Object> source) {
        Object raw = source.get("returnLineItems");
        if (raw instanceof Map<?, ?>) {
            Map<String, Object> connection = object(raw);
            Object nodes = connection.get("nodes");
            if (nodes instanceof List<?>) return maps(nodes);
            Object edges = connection.get("edges");
            if (edges instanceof List<?>) {
                return maps(edges).stream().map(edge -> object(edge.get("node"))).toList();
            }
        }
        return maps(source.get("return_line_items"));
    }

    private OrderReturnStatus mapStatus(String value) {
        if (contains(value, "DECLINED") || contains(value, "CANCELED")) return OrderReturnStatus.REJECTED;
        if (contains(value, "CLOSED") || contains(value, "COMPLETED")) return OrderReturnStatus.PLATFORM_PROCESSING;
        if (contains(value, "OPEN")) return OrderReturnStatus.AWAITING_RETURN;
        return OrderReturnStatus.PENDING_APPROVAL;
    }

    private String gidTail(String value) {
        return value.substring(value.lastIndexOf('/') + 1);
    }

    private OffsetDateTime date(String value) {
        try {
            return value == null ? null : OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private boolean contains(String value, String token) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(token);
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int integer(Object value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String text(Map<String, Object> map, String... keys) {
        return firstText(map, keys);
    }

    private String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null && !value.toString().isBlank()) return value.toString();
        }
        return null;
    }

    private Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> source)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(this::object).toList();
    }
}
