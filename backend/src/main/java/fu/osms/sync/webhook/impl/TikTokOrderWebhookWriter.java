package fu.osms.sync.webhook.impl;

import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.repository.WebhookEventRepository;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TikTokOrderWebhookWriter {

    private final WebhookEventRepository webhookEventRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final PlatformOrderInventoryService platformOrderInventoryService;

    @Transactional
    public void write(UUID eventId, Map<String, Object> detail) {
        WebhookEvent event = webhookEventRepository.findWithChannelById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook event not found: " + eventId));
        String externalOrderId = text(detail, "id", "order_id");
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalStateException("TikTok order detail is missing order id");
        }

        Order order = ensureOrder(event, externalOrderId);
        assertOrderBelongsToEvent(order, event);
        Map<String, Object> metadata = new HashMap<>();
        if (order.getPlatformMetadata() != null) {
            metadata.putAll(order.getPlatformMetadata());
        }
        Map<String, Object> tikTokMetadata = new HashMap<>(WebhookPayloadUtils.copyMap(metadata.get("tiktok")));
        mergeReverseMetadata(event, tikTokMetadata);

        Long incomingUpdateTime = epoch(detail.get("update_time"));
        Long storedUpdateTime = epoch(tikTokMetadata.get("lastOrderUpdateTime"));
        if (incomingUpdateTime != null && storedUpdateTime != null && incomingUpdateTime < storedUpdateTime) {
            metadata.put("tiktok", tikTokMetadata);
            order.setPlatformMetadata(metadata);
            orderRepository.save(order);
            complete(event);
            return;
        }

        Map<String, Object> payment = WebhookPayloadUtils.copyMap(detail.get("payment"));
        List<Map<String, Object>> items = maps(WebhookPayloadUtils.firstPresent(detail, "line_items", "order_line_items", "skus"));
        if (items.isEmpty()) {
            throw new IllegalStateException("TikTok order detail is missing line items");
        }
        List<Map<String, Object>> packages = maps(detail.get("packages"));
        String rawStatus = text(detail, "order_status", "status");
        Map<String, Object> recipientAddress = WebhookPayloadUtils.copyMap(detail.get("recipient_address"));

        order.setPlatform(PlatformType.TIKTOK);
        order.setChannel(event.getChannel());
        order.setChannelName(event.getChannel().getDisplayName());
        Optional<OrderStatus> mappedStatus = resolveStatus(rawStatus);
        mappedStatus.ifPresent(order::setStatus);
        order.setPaymentStatus(resolvePaymentStatus(rawStatus, payment));
        log.info("[TikTokOrderWebhook] Writing orderId={}, rawOrderStatus={}, paymentStatus={}",
                externalOrderId, rawStatus, order.getPaymentStatus());
        order.setBuyerName(text(recipientAddress, "name", "recipient_name"));
        order.setBuyerPhone(text(recipientAddress, "phone_number", "phone"));
        order.setShippingAddress(recipientAddress);
        order.setSubtotal(resolveSubtotal(payment, items));
        order.setDiscountAmount(resolveDiscount(payment));
        order.setShippingFee(decimal(payment, "shipping_fee"));
        order.setCurrency(defaultValue(text(payment, "currency"), "VND"));
        order.setNote(text(detail, "buyer_message", "buyer_note"));
        String platformCancelReason = text(detail, "cancel_reason");
        if (platformCancelReason != null && !platformCancelReason.isBlank()) {
            order.setCancelReason(platformCancelReason);
        }
        firstTrackingNumber(packages, items, detail).ifPresent(order::setTrackingNumber);
        if (mappedStatus.isPresent()) {
            order.setStatusChangedAt(OffsetDateTime.now());
        }

        tikTokMetadata.put("rawOrderStatus", rawStatus);
        if (incomingUpdateTime != null) {
            tikTokMetadata.put("lastOrderUpdateTime", incomingUpdateTime);
        }
        tikTokMetadata.put("payment", payment);
        tikTokMetadata.put("packages", packages);
        tikTokMetadata.put("recipientAddressStatus",
                hasRecipientAddressData(recipientAddress) ? "AVAILABLE" : "MASKED_OR_UNAVAILABLE");
        putIfPresent(tikTokMetadata, "buyerEmail", text(detail, "buyer_email"));
        putIfPresent(tikTokMetadata, "buyerUserId", text(detail, "user_id", "buyer_id"));
        if (mappedStatus.orElse(null) == OrderStatus.CANCELLED) {
            tikTokMetadata.put("pendingConfirmation", false);
            tikTokMetadata.put("cancelStatus", "CANCELLATION_REQUEST_COMPLETE");
            tikTokMetadata.put("cancelConfirmedAt", OffsetDateTime.now().toString());
        }
        metadata.put("tiktok", tikTokMetadata);
        order.setPlatformMetadata(metadata);

        Order savedOrder = orderRepository.save(order);
        replaceItems(savedOrder, items);
        platformOrderInventoryService.syncReservations(savedOrder);
        complete(event);
    }

    private Order ensureOrder(WebhookEvent event, String externalOrderId) {
        orderRepository.insertWebhookOrderIfAbsent(
                UUID.randomUUID(),
                event.getChannel().getId(),
                PlatformType.TIKTOK.name(),
                event.getChannel().getDisplayName(),
                externalOrderId
        );
        return orderRepository.findForUpdateByChannelIdAndExternalOrderId(event.getChannel().getId(), externalOrderId)
                .orElseThrow(() -> new IllegalStateException("Cannot create or load TikTok order"));
    }

    private void replaceItems(Order order, List<Map<String, Object>> items) {
        orderItemRepository.deleteByOrderId(order.getId());
        orderItemRepository.saveAll(items.stream().map(item -> buildOrderItem(order, item)).toList());
    }

    private OrderItem buildOrderItem(Order order, Map<String, Object> item) {
        ChannelProductVariant channelVariant = resolveChannelVariant(order.getChannel().getId(), item).orElse(null);
        String sku = text(item, "seller_sku", "sku");
        return OrderItem.builder()
                .order(order)
                .variant(channelVariant != null ? channelVariant.getVariant() : null)
                .channelVariant(channelVariant)
                .sku(sku)
                .name(defaultValue(text(item, "product_name", "sku_name", "name"), "TikTok item"))
                .quantity(integer(item.get("quantity"), 1))
                .unitPrice(decimal(item, "sale_price", "original_price", "price"))
                .discountAmount(decimal(item, "seller_discount").add(decimal(item, "platform_discount")))
                .costPrice(channelVariant != null ? channelVariant.getVariant().getCostPrice() : null)
                .build();
    }

    private Optional<ChannelProductVariant> resolveChannelVariant(UUID channelId, Map<String, Object> item) {
        String externalVariantId = text(item, "sku_id", "id");
        if (externalVariantId != null && !externalVariantId.isBlank()) {
            Optional<ChannelProductVariant> byId = channelProductVariantRepository
                    .findActiveByChannelIdAndExternalVariantId(channelId, externalVariantId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        String externalSku = text(item, "seller_sku", "sku");
        return externalSku == null || externalSku.isBlank()
                ? Optional.empty()
                : channelProductVariantRepository.findActiveByChannelIdAndExternalSku(channelId, externalSku);
    }

    private void mergeReverseMetadata(WebhookEvent event, Map<String, Object> tikTokMetadata) {
        if (!event.getEventType().contains("REVERSE")) {
            return;
        }
        Map<String, Object> data = WebhookPayloadUtils.copyMap(event.getRawPayload().get("data"));
        tikTokMetadata.put("reverse", data);
        String cancelStatus = text(data, "cancel_status", "reverse_status", "status");
        if (cancelStatus != null && !cancelStatus.isBlank()) {
            tikTokMetadata.put("cancelStatus", cancelStatus);
        }
        Long timestamp = epoch(event.getRawPayload().get("timestamp"));
        if (timestamp != null) {
            tikTokMetadata.put("lastReverseUpdateTime", timestamp);
            tikTokMetadata.put("lastCancellationUpdateTime", timestamp);
        }
    }

    private void complete(WebhookEvent event) {
        event.setStatus("PROCESSED");
        event.setErrorMessage(null);
        event.setProcessedAt(OffsetDateTime.now());
        webhookEventRepository.save(event);
    }

    private Optional<OrderStatus> resolveStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return Optional.empty();
        }
        return switch (rawStatus.toUpperCase()) {
            case "UNPAID", "ON_HOLD" -> Optional.of(OrderStatus.PENDING);
            case "AWAITING_SHIPMENT" -> Optional.of(OrderStatus.CONFIRMED);
            case "AWAITING_COLLECTION", "PARTIALLY_SHIPPING" -> Optional.of(OrderStatus.SHIPPED);
            case "IN_TRANSIT" -> Optional.of(OrderStatus.IN_TRANSIT);
            case "DELIVERED", "COMPLETED" -> Optional.of(OrderStatus.DELIVERED);
            case "CANCEL", "CANCELLED" -> Optional.of(OrderStatus.CANCELLED);
            default -> Optional.empty();
        };
    }

    private String resolvePaymentStatus(String rawStatus, Map<String, Object> payment) {
        String paymentStatus = text(payment, "status", "payment_status");
        if (paymentStatus != null && paymentStatus.toUpperCase().contains("REFUND")) {
            return "REFUNDED";
        }
        return "UNPAID".equalsIgnoreCase(rawStatus) ? "UNPAID" : "PAID";
    }

    private BigDecimal resolveSubtotal(Map<String, Object> payment, List<Map<String, Object>> items) {
        BigDecimal original = decimal(payment, "original_total_product_price");
        if (original.compareTo(BigDecimal.ZERO) > 0) {
            return original;
        }
        BigDecimal itemTotal = items.stream().map(item -> decimal(item, "original_price").multiply(BigDecimal.valueOf(integer(item.get("quantity"), 1))))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (itemTotal.compareTo(BigDecimal.ZERO) > 0) {
            return itemTotal;
        }
        return decimal(payment, "sub_total").add(resolveDiscount(payment));
    }

    private BigDecimal resolveDiscount(Map<String, Object> payment) {
        return decimal(payment, "seller_discount").add(decimal(payment, "platform_discount"));
    }

    private Optional<String> firstTrackingNumber(List<Map<String, Object>> packages, List<Map<String, Object>> items,
                                                 Map<String, Object> detail) {
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.addAll(packages);
        sources.addAll(items);
        sources.add(detail);
        return sources.stream().map(source -> text(source, "tracking_number", "tracking_no"))
                .filter(value -> value != null && !value.isBlank()).findFirst();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().filter(Map.class::isInstance).map(WebhookPayloadUtils::copyMap).toList();
    }

    private Long epoch(Object value) {
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String text(Map<String, Object> value, String... keys) {
        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(value, keys));
    }

    private BigDecimal decimal(Map<String, Object> value, String... keys) {
        return WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(value, keys));
    }

    private int integer(Object value, int defaultValue) {
        return WebhookPayloadUtils.integer(value, defaultValue);
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean hasRecipientAddressData(Map<String, Object> recipientAddress) {
        return text(recipientAddress, "name", "recipient_name", "phone_number", "phone",
                "full_address", "address_line1", "address_detail") != null;
    }

    private void putIfPresent(Map<String, Object> values, String key, String value) {
        if (value != null && !value.isBlank()) {
            values.put(key, value);
        }
    }

    private void assertOrderBelongsToEvent(Order order, WebhookEvent event) {
        if (order.getChannel() != null && !order.getChannel().getId().equals(event.getChannel().getId())) {
            throw new IllegalStateException("Resolved TikTok order belongs to another channel");
        }
    }
}
