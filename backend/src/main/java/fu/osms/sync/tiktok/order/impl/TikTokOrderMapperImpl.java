package fu.osms.sync.tiktok.order.impl;

import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.tiktok.order.TikTokOrderMapper;
import fu.osms.sync.tiktok.order.TikTokOrderWriteModel;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TikTokOrderMapperImpl implements TikTokOrderMapper {
    @Override
    public TikTokOrderWriteModel map(Map<String, Object> detail) {
        String id = text(detail, "id", "order_id");
        if (id == null || id.isBlank()) throw new IllegalStateException("TikTok order detail is missing order id");
        List<Map<String, Object>> rawItems = maps(WebhookPayloadUtils.firstPresent(detail, "line_items", "order_line_items", "skus"));
        if (rawItems.isEmpty()) throw new IllegalStateException("TikTok order detail is missing line items");
        Map<String, Object> payment = WebhookPayloadUtils.copyMap(detail.get("payment"));
        Map<String, Object> address = WebhookPayloadUtils.copyMap(detail.get("recipient_address"));
        List<Map<String, Object>> packages = maps(detail.get("packages"));
        String rawStatus = text(detail, "order_status", "status");
        List<TikTokOrderWriteModel.Item> items = rawItems.stream().map(this::item).toList();
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("payment", payment);
        metadata.put("packages", packages);
        metadata.put("recipientAddressStatus", hasAddress(address) ? "AVAILABLE" : "MASKED_OR_UNAVAILABLE");
        put(metadata, "buyerEmail", text(detail, "buyer_email"));
        put(metadata, "buyerUserId", text(detail, "user_id", "buyer_id"));
        return new TikTokOrderWriteModel(id, status(rawStatus), rawStatus, paymentStatus(rawStatus, payment),
                text(address, "name", "recipient_name"), text(address, "phone_number", "phone"), address,
                subtotal(payment, rawItems), discount(payment), decimal(payment, "shipping_fee"),
                fallback(text(payment, "currency"), "VND"), text(detail, "buyer_message", "buyer_note"),
                text(detail, "cancel_reason"), tracking(packages, rawItems, detail), epoch(detail.get("update_time")), metadata, items);
    }

    private TikTokOrderWriteModel.Item item(Map<String, Object> value) {
        return new TikTokOrderWriteModel.Item(
                text(value, "order_line_item_id", "line_item_id", "id"),
                text(value, "sku_id"), text(value, "seller_sku", "sku"),
                fallback(text(value, "product_name", "sku_name", "name"), "TikTok item"),
                WebhookPayloadUtils.integer(value.get("quantity"), 1), decimal(value, "sale_price", "original_price", "price"),
                decimal(value, "seller_discount").add(decimal(value, "platform_discount")));
    }
    private OrderStatus status(String value) {
        if (value == null) return null;
        return switch (value.toUpperCase()) {
            case "UNPAID", "ON_HOLD" -> OrderStatus.PENDING;
            case "AWAITING_SHIPMENT" -> OrderStatus.CONFIRMED;
            case "AWAITING_COLLECTION", "PARTIALLY_SHIPPING" -> OrderStatus.SHIPPED;
            case "IN_TRANSIT" -> OrderStatus.IN_TRANSIT;
            case "DELIVERED", "COMPLETED" -> OrderStatus.DELIVERED;
            case "CANCEL", "CANCELLED" -> OrderStatus.CANCELLED;
            default -> null;
        };
    }
    private String paymentStatus(String raw, Map<String, Object> payment) {
        String value = text(payment, "status", "payment_status");
        if (value != null && value.toUpperCase().contains("REFUND")) return "REFUNDED";
        return "UNPAID".equalsIgnoreCase(raw) ? "UNPAID" : "PAID";
    }
    private BigDecimal subtotal(Map<String, Object> payment, List<Map<String, Object>> items) {
        BigDecimal original = decimal(payment, "original_total_product_price");
        if (original.signum() > 0) return original;
        BigDecimal total = items.stream().map(item -> decimal(item, "original_price")
                .multiply(BigDecimal.valueOf(WebhookPayloadUtils.integer(item.get("quantity"), 1))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.signum() > 0 ? total : decimal(payment, "sub_total").add(discount(payment));
    }
    private BigDecimal discount(Map<String, Object> payment) {
        return decimal(payment, "seller_discount").add(decimal(payment, "platform_discount"));
    }
    private String tracking(List<Map<String, Object>> packages, List<Map<String, Object>> items, Map<String, Object> detail) {
        List<Map<String, Object>> all = new ArrayList<>(packages); all.addAll(items); all.add(detail);
        return all.stream().map(value -> text(value, "tracking_number", "tracking_no"))
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }
    private boolean hasAddress(Map<String, Object> value) { return text(value, "name", "phone_number", "full_address", "address_line1", "address_detail") != null; }
    private List<Map<String, Object>> maps(Object value) { if (!(value instanceof List<?> list)) return List.of(); return list.stream().filter(Map.class::isInstance).map(WebhookPayloadUtils::copyMap).toList(); }
    private String text(Map<String, Object> value, String... keys) { return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(value, keys)); }
    private BigDecimal decimal(Map<String, Object> value, String... keys) { return WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(value, keys)); }
    private Long epoch(Object value) { try { return value == null ? null : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException e) { return null; } }
    private String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private void put(Map<String, Object> map, String key, String value) { if (value != null && !value.isBlank()) map.put(key, value); }
}
