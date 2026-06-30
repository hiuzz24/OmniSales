package fu.osms.sync.service.impl;

import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.WebhookBusinessProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebhookBusinessProcessorImpl implements WebhookBusinessProcessor {

    private final OrderRepository orderRepository;

    @Override
    public String process(WebhookEvent event) {
        String eventType = event.getEventType() != null ? event.getEventType().toUpperCase() : "";
        if (!eventType.contains("ORDER")) {
            return "IGNORED";
        }

        Map<String, Object> payload = event.getRawPayload();
        String externalOrderId = text(firstPresent(payload, "id", "order_id", "trade_order_id", "orderId"));
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("Webhook order payload is missing external order id");
        }

        Order order = orderRepository.findByExternalOrderId(externalOrderId)
                .orElseGet(() -> Order.builder()
                        .platform(event.getPlatform())
                        .channel(event.getChannel())
                        .channelName(event.getChannel() != null ? event.getChannel().getDisplayName() : event.getPlatform().name())
                        .externalOrderId(externalOrderId)
                        .shippingAddress(new HashMap<>())
                        .build());

        order.setPlatform(event.getPlatform());
        order.setChannel(event.getChannel());
        order.setChannelName(event.getChannel() != null ? event.getChannel().getDisplayName() : event.getPlatform().name());
        order.setStatus(resolveStatus(eventType, payload));
        order.setPaymentStatus(resolvePaymentStatus(payload));
        order.setBuyerName(resolveBuyerName(payload));
        order.setBuyerPhone(resolveBuyerPhone(payload));
        order.setShippingAddress(resolveShippingAddress(payload));
        order.setSubtotal(decimal(firstPresent(payload, "subtotal_price", "subtotal", "price", "total_price")));
        order.setDiscountAmount(decimal(firstPresent(payload, "total_discounts", "discount_amount", "voucher")));
        order.setShippingFee(decimal(firstPresent(payload, "shipping_fee", "total_shipping_fee")));
        order.setCurrency(text(firstPresent(payload, "currency", "currency_code")) != null ? text(firstPresent(payload, "currency", "currency_code")) : "VND");
        order.setNote(text(firstPresent(payload, "note", "remarks")));
        order.setTrackingNumber(text(firstPresent(payload, "tracking_number", "tracking_code")));
        order.setCancelReason(text(firstPresent(payload, "cancel_reason", "cancelReason")));
        order.setStatusChangedAt(OffsetDateTime.now());

        orderRepository.save(order);
        return "PROCESSED";
    }

    private OrderStatus resolveStatus(String eventType, Map<String, Object> payload) {
        if (eventType.contains("CANCEL")) {
            return OrderStatus.CANCELLED;
        }
        String status = text(firstPresent(payload, "status", "order_status", "fulfillment_status"));
        if (status == null) {
            return OrderStatus.PENDING;
        }
        String normalized = status.toUpperCase();
        if (normalized.contains("CANCEL")) return OrderStatus.CANCELLED;
        if (normalized.contains("DELIVER")) return OrderStatus.DELIVERED;
        if (normalized.contains("SHIP")) return OrderStatus.SHIPPED;
        if (normalized.contains("PROCESS")) return OrderStatus.PROCESSING;
        if (normalized.contains("CONFIRM") || normalized.contains("PAID")) return OrderStatus.CONFIRMED;
        return OrderStatus.PENDING;
    }

    private String resolvePaymentStatus(Map<String, Object> payload) {
        String value = text(firstPresent(payload, "financial_status", "payment_status"));
        if (value == null) {
            return "UNPAID";
        }
        String normalized = value.toUpperCase();
        if (normalized.contains("PAID")) return "PAID";
        if (normalized.contains("REFUND")) return "REFUNDED";
        return "UNPAID";
    }

    @SuppressWarnings("unchecked")
    private String resolveBuyerName(Map<String, Object> payload) {
        Object customer = payload.get("customer");
        if (customer instanceof Map<?, ?> map) {
            Object firstName = map.get("first_name");
            Object lastName = map.get("last_name");
            String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            if (!fullName.isBlank()) return fullName;
            Object name = map.get("name");
            if (name != null) return name.toString();
        }
        return text(firstPresent(payload, "buyer_name", "customer_name", "name"));
    }

    @SuppressWarnings("unchecked")
    private String resolveBuyerPhone(Map<String, Object> payload) {
        Object customer = payload.get("customer");
        if (customer instanceof Map<?, ?> map && map.get("phone") != null) {
            return map.get("phone").toString();
        }
        return text(firstPresent(payload, "buyer_phone", "phone"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveShippingAddress(Map<String, Object> payload) {
        Object address = firstPresent(payload, "shipping_address", "address", "recipient_address");
        if (address instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, value) -> result.put(String.valueOf(key), value));
            return result;
        }
        return new HashMap<>();
    }

    private Object firstPresent(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value != null) return value;
        }
        return null;
    }

    private String text(Object value) {
        return value != null ? value.toString() : null;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || value.toString().isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }
}
