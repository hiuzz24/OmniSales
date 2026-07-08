package fu.osms.sync.webhook.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ShopifyOrderWebhookProcessor implements PlatformOrderWebhookProcessor {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.SHOPIFY;
    }

    @Override
    @Transactional
    public String process(WebhookEvent event) {
        Map<String, Object> payload = event.getRawPayload();
        String externalOrderId = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "id", "order_id"));
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("Shopify webhook payload is missing order id");
        }

        Order order = ensureOrder(event, externalOrderId);
        assertOrderBelongsToEvent(order, event);
        order.setPlatform(event.getPlatform());
        order.setChannel(event.getChannel());
        order.setChannelName(event.getChannel().getDisplayName());
        order.setStatus(resolveStatus(event.getEventType(), payload));
        order.setPaymentStatus(resolvePaymentStatus(payload));
        order.setBuyerName(resolveBuyerName(payload));
        order.setBuyerPhone(resolveBuyerPhone(payload));
        order.setShippingAddress(WebhookPayloadUtils.copyMap(WebhookPayloadUtils.firstPresent(payload, "shipping_address", "address")));
        order.setSubtotal(WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(payload, "subtotal_price", "subtotal")));
        order.setDiscountAmount(WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(payload, "total_discounts", "discount_amount")));
        order.setShippingFee(resolveShippingFee(payload));
        order.setCurrency(resolveCurrency(payload));
        order.setNote(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "note", "remarks")));
        order.setTrackingNumber(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "tracking_number", "tracking_code")));
        order.setCancelReason(WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "cancel_reason", "cancelReason")));
        order.setStatusChangedAt(OffsetDateTime.now());

        Order savedOrder = orderRepository.save(order);
        syncOrderItems(savedOrder, payload);
        return "PROCESSED";
    }

    private Order ensureOrder(WebhookEvent event, String externalOrderId) {
        orderRepository.insertWebhookOrderIfAbsent(
                UUID.randomUUID(),
                event.getChannel().getId(),
                event.getPlatform().name(),
                event.getChannel().getDisplayName(),
                externalOrderId
        );
        return orderRepository.findForUpdateByChannelIdAndExternalOrderId(event.getChannel().getId(), externalOrderId)
                .orElseThrow(() -> new IllegalStateException("Cannot create or load Shopify order"));
    }

    private void syncOrderItems(Order order, Map<String, Object> payload) {
        Object lineItems = payload.get("line_items");
        if (!(lineItems instanceof List<?> items)) {
            return;
        }

        orderItemRepository.deleteByOrderId(order.getId());
        List<OrderItem> orderItems = items.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(item -> buildOrderItem(order, WebhookPayloadUtils.copyMap(item)))
                .toList();
        orderItemRepository.saveAll(orderItems);
    }

    private OrderItem buildOrderItem(Order order, Map<String, Object> lineItem) {
        String sku = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(lineItem, "sku"));
        String name = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(lineItem, "name", "title"));
        Integer quantity = WebhookPayloadUtils.integer(WebhookPayloadUtils.firstPresent(lineItem, "quantity", "current_quantity"), 1);
        BigDecimal unitPrice = WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(lineItem, "price"));
        BigDecimal discountAmount = WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(lineItem, "total_discount"));
        ChannelProductVariant channelVariant = resolveChannelVariant(order.getChannel().getId(), lineItem).orElse(null);

        return OrderItem.builder()
                .order(order)
                .variant(channelVariant != null ? channelVariant.getVariant() : null)
                .channelVariant(channelVariant)
                .sku(sku)
                .name(name != null && !name.isBlank() ? name : "Shopify item")
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(discountAmount)
                .costPrice(channelVariant != null ? channelVariant.getVariant().getCostPrice() : null)
                .build();
    }

    private Optional<ChannelProductVariant> resolveChannelVariant(UUID channelId, Map<String, Object> lineItem) {
        String externalVariantId = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(lineItem, "variant_id"));
        if (externalVariantId != null && !externalVariantId.isBlank()) {
            return channelProductVariantRepository.findActiveByChannelIdAndExternalVariantId(channelId, externalVariantId);
        }
        return Optional.empty();
    }

    private OrderStatus resolveStatus(String eventType, Map<String, Object> payload) {
        String normalizedEventType = eventType != null ? eventType.toUpperCase() : "";
        if (normalizedEventType.contains("CANCEL") || WebhookPayloadUtils.firstPresent(payload, "cancelled_at", "cancel_reason") != null) {
            return OrderStatus.CANCELLED;
        }

        if (hasDeliveredFulfillment(payload)) {
            return OrderStatus.DELIVERED;
        }

        String fulfillmentStatus = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "fulfillment_status"));
        if (fulfillmentStatus != null) {
            String normalizedFulfillment = fulfillmentStatus.toUpperCase();
            if (normalizedFulfillment.contains("DELIVER")) {
                return OrderStatus.DELIVERED;
            }
            if (normalizedFulfillment.contains("FULFILLED")
                    || normalizedFulfillment.contains("SHIP")
                    || normalizedFulfillment.contains("PARTIAL")) {
                return OrderStatus.SHIPPED;
            }
        }

        String financialStatus = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "financial_status", "payment_status"));
        if (financialStatus != null && financialStatus.toUpperCase().contains("PAID")) {
            return OrderStatus.CONFIRMED;
        }

        return OrderStatus.PENDING;
    }

    private boolean hasDeliveredFulfillment(Map<String, Object> payload) {
        Object fulfillments = payload.get("fulfillments");
        if (!(fulfillments instanceof List<?> fulfillmentList)) {
            return false;
        }

        return fulfillmentList.stream()
                .filter(fulfillment -> fulfillment instanceof Map<?, ?>)
                .map(WebhookPayloadUtils::copyMap)
                .map(fulfillment -> WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(fulfillment, "shipment_status")))
                .filter(status -> status != null && !status.isBlank())
                .map(String::toUpperCase)
                .anyMatch(status -> status.contains("DELIVER"));
    }

    private String resolvePaymentStatus(Map<String, Object> payload) {
        String value = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "financial_status", "payment_status"));
        if (value == null) {
            return "UNPAID";
        }
        String normalized = value.toUpperCase();
        if (normalized.contains("REFUND")) {
            return "REFUNDED";
        }
        if (normalized.contains("PAID")) {
            return "PAID";
        }
        return "UNPAID";
    }

    private String resolveBuyerName(Map<String, Object> payload) {
        Object customer = payload.get("customer");
        if (customer instanceof Map<?, ?> map) {
            Object firstName = map.get("first_name");
            Object lastName = map.get("last_name");
            String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
            Object name = map.get("name");
            if (name != null) {
                return name.toString();
            }
        }
        String shippingName = resolveNameFromAddress(payload.get("shipping_address"));
        if (shippingName != null) {
            return shippingName;
        }

        String billingName = resolveNameFromAddress(payload.get("billing_address"));
        if (billingName != null) {
            return billingName;
        }

        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "buyer_name", "customer_name"));
    }

    private String resolveNameFromAddress(Object address) {
        if (address instanceof Map<?, ?> map) {
            Object name = map.get("name");
            if (name != null && !name.toString().isBlank()) {
                return name.toString();
            }

            Object firstName = map.get("first_name");
            Object lastName = map.get("last_name");
            String fullName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            if (!fullName.isBlank()) {
                return fullName;
            }
        }
        return null;
    }

    private String resolveBuyerPhone(Map<String, Object> payload) {
        Object customer = payload.get("customer");
        if (customer instanceof Map<?, ?> map) {
            Object phone = map.get("phone");
            if (phone != null && !phone.toString().isBlank()) {
                return phone.toString();
            }

            String defaultAddressPhone = resolvePhoneFromAddress(map.get("default_address"));
            if (defaultAddressPhone != null) {
                return defaultAddressPhone;
            }
        }

        String shippingPhone = resolvePhoneFromAddress(payload.get("shipping_address"));
        if (shippingPhone != null) {
            return shippingPhone;
        }

        String billingPhone = resolvePhoneFromAddress(payload.get("billing_address"));
        if (billingPhone != null) {
            return billingPhone;
        }

        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "buyer_phone", "phone"));
    }

    private String resolvePhoneFromAddress(Object address) {
        if (address instanceof Map<?, ?> map) {
            Object phone = map.get("phone");
            if (phone != null && !phone.toString().isBlank()) {
                return phone.toString();
            }
        }
        return null;
    }

    private BigDecimal resolveShippingFee(Map<String, Object> payload) {
        Object value = WebhookPayloadUtils.firstPresent(payload, "shipping_fee", "total_shipping_fee");
        if (value != null) {
            return WebhookPayloadUtils.decimal(value);
        }

        Object shippingLines = payload.get("shipping_lines");
        if (shippingLines instanceof List<?> lines && !lines.isEmpty() && lines.get(0) instanceof Map<?, ?> line) {
            Object price = line.get("price");
            if (price != null) {
                return WebhookPayloadUtils.decimal(price);
            }
        }

        Object priceSet = payload.get("total_shipping_price_set");
        if (priceSet instanceof Map<?, ?> map) {
            Object shopMoney = map.get("shop_money");
            if (shopMoney instanceof Map<?, ?> shopMoneyMap) {
                return WebhookPayloadUtils.decimal(shopMoneyMap.get("amount"));
            }
        }

        return BigDecimal.ZERO;
    }

    private String resolveCurrency(Map<String, Object> payload) {
        String currency = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, "currency", "currency_code"));
        return currency != null && !currency.isBlank() ? currency : "VND";
    }

    private void assertOrderBelongsToEvent(Order order, WebhookEvent event) {
        if (order.getChannel() != null && !order.getChannel().getId().equals(event.getChannel().getId())) {
            throw new IllegalStateException("Resolved Shopify order belongs to another channel");
        }
        if (order.getPlatform() != null && order.getPlatform() != event.getPlatform()) {
            throw new IllegalStateException("Resolved Shopify order belongs to another platform");
        }
    }
}
