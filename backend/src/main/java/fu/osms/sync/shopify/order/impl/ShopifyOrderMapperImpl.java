package fu.osms.sync.shopify.order.impl;

import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.shopify.order.ShopifyOrderMapper;
import fu.osms.sync.shopify.order.ShopifyOrderWriteModel;
import fu.osms.sync.order.importing.PlatformOrderTimestampParser;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class ShopifyOrderMapperImpl implements ShopifyOrderMapper {

    @Override
    public ShopifyOrderWriteModel mapWebhook(String eventType, Map<String, Object> payload) {
        return map(eventType, payload);
    }

    @Override
    public ShopifyOrderWriteModel mapManual(Map<String, Object> payload) {
        return map(null, payload);
    }

    private ShopifyOrderWriteModel map(String eventType, Map<String, Object> payload) {
        String id = text(payload, "id", "order_id");
        if (id == null || id.isBlank()) throw new IllegalStateException("Shopify order is missing order id");
        List<ShopifyOrderWriteModel.Item> items = maps(payload.get("line_items")).stream().map(this::item).toList();
        if (items.isEmpty()) throw new IllegalStateException("Shopify order is missing line items");
        Map<String, Object> address = WebhookPayloadUtils.copyMap(
                WebhookPayloadUtils.firstPresent(payload, "shipping_address", "address"));
        return new ShopifyOrderWriteModel(id,
                PlatformOrderTimestampParser.parse(WebhookPayloadUtils.firstPresent(
                        payload, "created_at", "createdAt", "order_created_at")),
                status(eventType, payload), payment(payload), buyerName(payload),
                buyerPhone(payload), address, decimal(payload, "subtotal_price", "subtotal"),
                decimal(payload, "total_discounts", "discount_amount"), shippingFee(payload),
                fallback(text(payload, "currency", "currency_code"), "VND"), text(payload, "note", "remarks"),
                tracking(payload), text(payload, "cancel_reason", "cancelReason"), items);
    }

    private ShopifyOrderWriteModel.Item item(Map<String, Object> item) {
        return new ShopifyOrderWriteModel.Item(text(item, "id", "line_item_id"),
                text(item, "variant_id"), text(item, "sku"),
                fallback(text(item, "name", "title"), "Shopify item"),
                WebhookPayloadUtils.integer(WebhookPayloadUtils.firstPresent(item, "quantity", "current_quantity"), 1),
                decimal(item, "price"), decimal(item, "total_discount"));
    }

    private OrderStatus status(String eventType, Map<String, Object> payload) {
        if ((eventType != null && eventType.toUpperCase().contains("CANCEL"))
                || WebhookPayloadUtils.firstPresent(payload, "cancelled_at", "cancel_reason") != null) return OrderStatus.CANCELLED;
        if (hasDeliveredFulfillment(payload)) return OrderStatus.DELIVERED;
        String fulfillment = text(payload, "fulfillment_status");
        if (contains(fulfillment, "DELIVER")) return OrderStatus.DELIVERED;
        if (contains(fulfillment, "FULFILLED") || contains(fulfillment, "SHIP") || contains(fulfillment, "PARTIAL"))
            return OrderStatus.SHIPPED;
        return OrderStatus.PENDING;
    }

    private boolean hasDeliveredFulfillment(Map<String, Object> payload) {
        return maps(payload.get("fulfillments")).stream().map(value -> text(value, "shipment_status", "status"))
                .anyMatch(value -> contains(value, "DELIVER"));
    }

    private String payment(Map<String, Object> payload) {
        String value = text(payload, "financial_status", "payment_status");
        if (contains(value, "REFUND")) return "REFUNDED";
        return contains(value, "PAID") || contains(value, "AUTHORIZED") ? "PAID" : "UNPAID";
    }

    private String buyerName(Map<String, Object> payload) {
        Map<String, Object> customer = WebhookPayloadUtils.copyMap(payload.get("customer"));
        String name = compact(text(customer, "first_name"), text(customer, "last_name"));
        if (name != null) return name;
        name = addressName(payload.get("shipping_address"));
        return name != null ? name : addressName(payload.get("billing_address"));
    }

    private String buyerPhone(Map<String, Object> payload) {
        Map<String, Object> customer = WebhookPayloadUtils.copyMap(payload.get("customer"));
        String phone = text(customer, "phone");
        if (phone != null && !phone.isBlank()) return phone;
        phone = addressPhone(payload.get("shipping_address"));
        return phone != null ? phone : addressPhone(payload.get("billing_address"));
    }

    private String addressName(Object raw) {
        Map<String, Object> value = WebhookPayloadUtils.copyMap(raw);
        String name = text(value, "name");
        return name != null && !name.isBlank() ? name : compact(text(value, "first_name"), text(value, "last_name"));
    }
    private String addressPhone(Object raw) { return text(WebhookPayloadUtils.copyMap(raw), "phone"); }

    private BigDecimal shippingFee(Map<String, Object> payload) {
        Object direct = WebhookPayloadUtils.firstPresent(payload, "shipping_fee", "total_shipping_fee");
        if (direct != null) return WebhookPayloadUtils.decimal(direct);
        List<Map<String, Object>> lines = maps(payload.get("shipping_lines"));
        if (!lines.isEmpty()) return decimal(lines.get(0), "price");
        Map<String, Object> priceSet = WebhookPayloadUtils.copyMap(payload.get("total_shipping_price_set"));
        return decimal(WebhookPayloadUtils.copyMap(priceSet.get("shop_money")), "amount");
    }

    private String tracking(Map<String, Object> payload) {
        String direct = text(payload, "tracking_number", "tracking_code");
        if (direct != null) return direct;
        return maps(payload.get("fulfillments")).stream().map(value -> text(value, "tracking_number"))
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse(null);
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(WebhookPayloadUtils::copyMap).toList();
    }
    private String text(Map<String, Object> source, String... keys) { return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(source, keys)); }
    private BigDecimal decimal(Map<String, Object> source, String... keys) { return WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(source, keys)); }
    private boolean contains(String value, String token) { return value != null && value.toUpperCase().contains(token); }
    private String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String compact(String... values) {
        String result = String.join(" ", java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList());
        return result.isBlank() ? null : result;
    }
}
