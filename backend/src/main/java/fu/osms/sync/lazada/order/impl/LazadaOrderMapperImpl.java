package fu.osms.sync.lazada.order.impl;

import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.lazada.order.LazadaOrderMapper;
import fu.osms.sync.lazada.order.LazadaOrderStatusContext;
import fu.osms.sync.lazada.order.LazadaOrderWriteModel;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class LazadaOrderMapperImpl implements LazadaOrderMapper {

    @Override
    public LazadaOrderWriteModel map(LazadaOrderStatusContext context, Map<String, Object> orderData,
                                     List<Map<String, Object>> items) {
        String externalOrderId = text(orderData, "order_id", "trade_order_id", "id");
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalStateException("Lazada order detail is missing order id");
        }
        List<LazadaOrderWriteModel.Item> mappedItems = items.stream().map(this::mapItem).toList();
        if (mappedItems.isEmpty()) {
            throw new IllegalStateException("Lazada order detail is missing items");
        }
        Map<String, Object> address = shippingAddress(orderData);
        return new LazadaOrderWriteModel(
                externalOrderId, status(context, items), paymentStatus(context, orderData, items),
                compact(text(orderData, "customer_first_name"), text(orderData, "customer_last_name")),
                firstNonBlank(text(address, "phone", "phone2"),
                        text(WebhookPayloadUtils.copyMap(orderData.get("address_billing")), "phone", "phone2")),
                address, subtotal(orderData, items), discount(orderData, items), decimal(orderData, "shipping_fee"),
                currency(orderData, items), text(orderData, "buyer_note", "remarks"), tracking(items), mappedItems);
    }

    private LazadaOrderWriteModel.Item mapItem(Map<String, Object> item) {
        return new LazadaOrderWriteModel.Item(text(item, "order_item_id", "order_line_id", "trade_order_line_id"),
                text(item, "sku_id"), text(item, "sku", "shop_sku"),
                fallback(text(item, "name"), "Lazada item"), 1,
                decimal(item, "item_price", "paid_price"), decimal(item, "voucher_amount"));
    }

    private OrderStatus status(LazadaOrderStatusContext context, List<Map<String, Object>> items) {
        if (contains(context.reverseStatus(), "CANCEL")) return OrderStatus.CANCELLED;
        List<String> statuses = new ArrayList<>(context.statuses());
        items.stream().map(item -> text(item, "status")).filter(value -> value != null).forEach(statuses::add);
        if (statuses.stream().anyMatch(value -> contains(value, "CANCEL"))) return OrderStatus.CANCELLED;
        if (statuses.stream().anyMatch(value -> contains(value, "DELIVER"))) return OrderStatus.DELIVERED;
        if (statuses.stream().anyMatch(value -> contains(value, "SHIP") && !contains(value, "READY_TO_SHIP"))) return OrderStatus.IN_TRANSIT;
        if (statuses.stream().anyMatch(value -> contains(value, "READY_TO_SHIP"))) return OrderStatus.SHIPPED;
        if (statuses.stream().anyMatch(value -> contains(value, "PACK") || contains(value, "PROCESS"))) return OrderStatus.PROCESSING;
        return OrderStatus.PENDING;
    }

    private String paymentStatus(LazadaOrderStatusContext context, Map<String, Object> orderData,
                                 List<Map<String, Object>> items) {
        if (items.stream().anyMatch(item -> contains(text(item, "stage_pay_status"), "UNPAID"))) return "UNPAID";
        if (contains(text(orderData, "payment_method"), "COD")) return "UNPAID";
        List<String> statuses = new ArrayList<>(context.statuses());
        items.stream().map(item -> text(item, "status")).filter(value -> value != null).forEach(statuses::add);
        if (statuses.stream().anyMatch(value -> contains(value, "REFUND"))) return "REFUNDED";
        return statuses.stream().anyMatch(value -> contains(value, "PAID") || contains(value, "PACK")
                || contains(value, "READY_TO_SHIP") || contains(value, "SHIP") || contains(value, "DELIVER"))
                ? "PAID" : "UNPAID";
    }

    private Map<String, Object> shippingAddress(Map<String, Object> orderData) {
        Map<String, Object> source = WebhookPayloadUtils.copyMap(orderData.get("address_shipping"));
        if (source.isEmpty()) return new HashMap<>();
        Map<String, Object> result = new HashMap<>(source);
        put(result, "name", compact(text(source, "first_name"), text(source, "last_name")));
        put(result, "phone", text(source, "phone", "phone2"));
        put(result, "address1", text(source, "address1"));
        put(result, "address2", text(source, "address2"));
        put(result, "city", text(source, "city"));
        put(result, "district", text(source, "addressDistrict"));
        put(result, "province", text(source, "province"));
        put(result, "zip", text(source, "post_code", "zip"));
        put(result, "country", text(source, "country"));
        return result;
    }

    private BigDecimal subtotal(Map<String, Object> order, List<Map<String, Object>> items) {
        BigDecimal value = items.stream().map(item -> decimal(item, "item_price", "paid_price"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return value.signum() > 0 ? value : decimal(order, "price");
    }

    private BigDecimal discount(Map<String, Object> order, List<Map<String, Object>> items) {
        BigDecimal value = items.stream().map(item -> decimal(item, "voucher_amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return value.signum() > 0 ? value : decimal(order, "voucher");
    }

    private String currency(Map<String, Object> order, List<Map<String, Object>> items) {
        return items.stream().map(item -> text(item, "currency")).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(fallback(text(order, "currency"), "VND"));
    }

    private String tracking(List<Map<String, Object>> items) {
        return items.stream().map(item -> text(item, "tracking_code")).filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(null);
    }

    private String text(Map<String, Object> source, String... keys) {
        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(source, keys));
    }
    private BigDecimal decimal(Map<String, Object> source, String... keys) {
        return WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(source, keys));
    }
    private boolean contains(String value, String token) { return value != null && value.toUpperCase().contains(token); }
    private String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String firstNonBlank(String first, String second) { return first != null && !first.isBlank() ? first : second; }
    private String compact(String... values) {
        String result = String.join(" ", java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList());
        return result.isBlank() ? null : result;
    }
    private void put(Map<String, Object> target, String key, String value) { if (value != null && !value.isBlank()) target.put(key, value); }
}
