package fu.osms.sync.webhook.impl;

import fu.osms.channel.entity.ChannelCredential;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelCredentialRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.lazada.service.LazadaApiClient;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class LazadaOrderWebhookProcessor implements PlatformOrderWebhookProcessor {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ChannelCredentialRepository channelCredentialRepository;
    private final ChannelProductVariantRepository channelProductVariantRepository;
    private final LazadaApiClient lazadaApiClient;
    private final PlatformOrderInventoryService platformOrderInventoryService;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    @Transactional
    public String process(WebhookEvent event) {
        Map<String, Object> payload = event.getRawPayload();
        String externalOrderId = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "trade_order_id"));
        if (externalOrderId == null || externalOrderId.isBlank()) {
            throw new IllegalArgumentException("Lazada webhook payload is missing data.trade_order_id");
        }

        ChannelCredential credential = resolveCredential(event);
        Long tokenExpiresAt = credential.getTokenExpiresAt() != null
                ? credential.getTokenExpiresAt().toEpochSecond()
                : null;
        Map<String, Object> orderData = fetchOrderDetail(externalOrderId, credential.getAccessToken(), tokenExpiresAt);
        List<Map<String, Object>> orderItemsData = fetchOrderItems(externalOrderId, credential.getAccessToken(), tokenExpiresAt);

        Order order = ensureOrder(event, externalOrderId);
        assertOrderBelongsToEvent(order, event);

        order.setPlatform(event.getPlatform());
        order.setChannel(event.getChannel());
        order.setChannelName(event.getChannel().getDisplayName());
        order.setStatus(resolveStatus(payload, orderItemsData));
        order.setPaymentStatus(resolvePaymentStatus(payload, orderData, orderItemsData));
        order.setBuyerName(resolveBuyerName(orderData));
        order.setBuyerPhone(resolveBuyerPhone(orderData));
        order.setShippingAddress(resolveShippingAddress(orderData));
        order.setSubtotal(resolveSubtotal(orderData, orderItemsData));
        order.setDiscountAmount(resolveDiscountAmount(orderData, orderItemsData));
        order.setShippingFee(WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(orderData, "shipping_fee")));
        order.setCurrency(resolveCurrency(orderData, orderItemsData));
        order.setNote(resolveNote(orderData));
        resolveTrackingNumber(orderItemsData).ifPresent(order::setTrackingNumber);
        order.setStatusChangedAt(OffsetDateTime.now());

        Order savedOrder = orderRepository.save(order);
        syncOrderItems(savedOrder, orderItemsData);
        platformOrderInventoryService.syncReservations(savedOrder);
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
                .orElseThrow(() -> new IllegalStateException("Cannot create or load Lazada order"));
    }

    private ChannelCredential resolveCredential(WebhookEvent event) {
        ChannelCredential credential = channelCredentialRepository.findByChannelId(event.getChannel().getId())
                .orElseThrow(() -> new IllegalStateException("Channel credential not connected"));
        if (!"CONNECTED".equals(credential.getConnectionState())
                || credential.getAccessToken() == null
                || credential.getAccessToken().isBlank()) {
            throw new IllegalStateException("Channel credential not connected");
        }
        return credential;
    }

    private Map<String, Object> fetchOrderDetail(String orderId, String accessToken, Long tokenExpiresAt) {
        log.info("[lazada fetch order]");
        String response = lazadaApiClient.executeGet("/order/get", Map.of("order_id", orderId), accessToken, tokenExpiresAt);
        Map<String, Object> body = WebhookPayloadUtils.parseObject(response, "Lazada order detail response is invalid");
        assertLazadaSuccess(body, "Lazada order detail API returned error");
        return WebhookPayloadUtils.copyMap(body.get("data"));
    }

    private List<Map<String, Object>> fetchOrderItems(String orderId, String accessToken, Long tokenExpiresAt) {
        log.info("[lazada fetch order item]");
        String response = lazadaApiClient.executeGet("/order/items/get", Map.of("order_id", orderId), accessToken, tokenExpiresAt);
        Map<String, Object> body = WebhookPayloadUtils.parseObject(response, "Lazada order items response is invalid");
        assertLazadaSuccess(body, "Lazada order items API returned error");
        Object data = body.get("data");
        if (!(data instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(item -> item instanceof Map<?, ?>)
                .map(WebhookPayloadUtils::copyMap)
                .toList();
    }

    private void assertLazadaSuccess(Map<String, Object> body, String errorMessage) {
        String code = WebhookPayloadUtils.text(body.get("code"));
        if (code != null && !"0".equals(code)) {
            String message = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(body, "message", "msg"));
            throw new IllegalStateException(errorMessage + (message != null ? ": " + message : ""));
        }
    }

    private void syncOrderItems(Order order, List<Map<String, Object>> itemsData) {
        orderItemRepository.deleteByOrderId(order.getId());
        List<OrderItem> orderItems = itemsData.stream()
                .map(item -> buildOrderItem(order, item))
                .toList();
        orderItemRepository.saveAll(orderItems);
    }

    private OrderItem buildOrderItem(Order order, Map<String, Object> itemData) {
        String sku = firstText(itemData, "sku", "shop_sku");
        String name = firstText(itemData, "name");
        BigDecimal unitPrice = firstDecimal(itemData, "item_price", "paid_price");
        BigDecimal discountAmount = firstDecimal(itemData, "voucher_amount");
        ChannelProductVariant channelVariant = resolveChannelVariant(order.getChannel().getId(), itemData).orElse(null);

        return OrderItem.builder()
                .order(order)
                .variant(channelVariant != null ? channelVariant.getVariant() : null)
                .channelVariant(channelVariant)
                .sku(sku)
                .name(name != null && !name.isBlank() ? name : "Lazada item")
                .quantity(1)
                .unitPrice(unitPrice)
                .discountAmount(discountAmount)
                .build();
    }

    private Optional<ChannelProductVariant> resolveChannelVariant(UUID channelId, Map<String, Object> itemData) {
        String skuId = firstText(itemData, "sku_id");
        if (skuId == null || skuId.isBlank()) {
            return Optional.empty();
        }
        return channelProductVariantRepository.findActiveByChannelIdAndExternalVariantId(channelId, skuId);
    }

    private OrderStatus resolveStatus(Map<String, Object> payload, List<Map<String, Object>> itemsData) {
        String reverseStatus = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "reverse_status"));
        if (contains(reverseStatus, "CANCEL")) {
            return OrderStatus.CANCELLED;
        }

        String status = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "order_status"));
        if (status == null || status.isBlank()) {
            return OrderStatus.PENDING;
        }

        String normalized = status.toUpperCase();
        if (normalized.contains("CANCEL") || itemsData.stream().anyMatch(item -> contains(firstText(item, "status"), "CANCEL"))) {
            return OrderStatus.CANCELLED;
        }
        if (normalized.contains("DELIVER")) {
            return OrderStatus.DELIVERED;
        }
        if (normalized.contains("SHIP") && !normalized.contains("READY_TO_SHIP")) {
            return OrderStatus.IN_TRANSIT;
        }
        if (normalized.contains("PACK") || normalized.contains("READY_TO_SHIP") || normalized.contains("PROCESS")) {
            return normalized.contains("READY_TO_SHIP") ? OrderStatus.SHIPPED : OrderStatus.PROCESSING;
        }
        return OrderStatus.PENDING;
    }

    private String resolvePaymentStatus(Map<String, Object> payload, Map<String, Object> orderData, List<Map<String, Object>> itemsData) {
        if (itemsData.stream().anyMatch(item -> contains(firstText(item, "stage_pay_status"), "UNPAID"))) {
            return "UNPAID";
        }
        String paymentMethod = firstText(orderData, "payment_method");
        if (contains(paymentMethod, "COD")) {
            return "UNPAID";
        }
        String status = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "order_status"));
        if (contains(status, "PAID") || contains(status, "PACK") || contains(status, "READY_TO_SHIP")
                || contains(status, "SHIP") || contains(status, "DELIVER")) {
            return "PAID";
        }
        if (contains(status, "REFUND")) {
            return "REFUNDED";
        }
        return "UNPAID";
    }

    private String resolveBuyerName(Map<String, Object> orderData) {
        return compact(firstText(orderData, "customer_first_name"), firstText(orderData, "customer_last_name"));
    }

    private String resolveBuyerPhone(Map<String, Object> orderData) {
        Map<String, Object> shippingAddress = WebhookPayloadUtils.copyMap(orderData.get("address_shipping"));
        String shippingPhone = firstText(shippingAddress, "phone", "phone2");
        if (shippingPhone != null && !shippingPhone.isBlank()) {
            return shippingPhone;
        }
        Map<String, Object> billingAddress = WebhookPayloadUtils.copyMap(orderData.get("address_billing"));
        return firstText(billingAddress, "phone", "phone2");
    }

    private Map<String, Object> resolveShippingAddress(Map<String, Object> orderData) {
        Map<String, Object> source = WebhookPayloadUtils.copyMap(orderData.get("address_shipping"));
        if (source.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Object> address = new HashMap<>(source);
        String name = compact(firstText(source, "first_name"), firstText(source, "last_name"));
        putIfPresent(address, "name", name);
        putIfPresent(address, "phone", firstText(source, "phone", "phone2"));
        putIfPresent(address, "address1", firstText(source, "address1"));
        putIfPresent(address, "address2", firstText(source, "address2"));
        putIfPresent(address, "city", firstText(source, "city"));
        putIfPresent(address, "district", firstText(source, "addressDistrict"));
        putIfPresent(address, "province", firstText(source, "province"));
        putIfPresent(address, "zip", firstText(source, "post_code", "zip"));
        putIfPresent(address, "country", firstText(source, "country"));
        return address;
    }

    private BigDecimal resolveSubtotal(Map<String, Object> orderData, List<Map<String, Object>> itemsData) {
        BigDecimal itemTotal = itemsData.stream()
                .map(item -> firstDecimal(item, "item_price", "paid_price"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return itemTotal.compareTo(BigDecimal.ZERO) > 0 ? itemTotal : firstDecimal(orderData, "price");
    }

    private BigDecimal resolveDiscountAmount(Map<String, Object> orderData, List<Map<String, Object>> itemsData) {
        BigDecimal itemDiscount = itemsData.stream()
                .map(item -> firstDecimal(item, "voucher_amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return itemDiscount.compareTo(BigDecimal.ZERO) > 0 ? itemDiscount : firstDecimal(orderData, "voucher");
    }

    private String resolveCurrency(Map<String, Object> orderData, List<Map<String, Object>> itemsData) {
        String itemCurrency = itemsData.stream()
                .map(item -> firstText(item, "currency"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
        if (itemCurrency != null) {
            return itemCurrency;
        }
        String orderCurrency = firstText(orderData, "currency");
        return orderCurrency != null && !orderCurrency.isBlank() ? orderCurrency : "VND";
    }

    private String resolveNote(Map<String, Object> orderData) {
        return firstText(orderData, "buyer_note", "remarks");
    }

    private Optional<String> resolveTrackingNumber(List<Map<String, Object>> itemsData) {
        return itemsData.stream()
                .map(item -> firstText(item, "tracking_code"))
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String firstText(Map<String, Object> payload, String... keys) {
        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(payload, keys));
    }

    private BigDecimal firstDecimal(Map<String, Object> payload, String... keys) {
        return WebhookPayloadUtils.decimal(WebhookPayloadUtils.firstPresent(payload, keys));
    }

    private String compact(String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(value);
        }
        return result.isEmpty() ? null : result.toString();
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private boolean contains(String value, String token) {
        return value != null && value.toUpperCase().contains(token);
    }

    private void assertOrderBelongsToEvent(Order order, WebhookEvent event) {
        if (order.getChannel() != null && !order.getChannel().getId().equals(event.getChannel().getId())) {
            throw new IllegalStateException("Resolved Lazada order belongs to another channel");
        }
        if (order.getPlatform() != null && order.getPlatform() != event.getPlatform()) {
            throw new IllegalStateException("Resolved Lazada order belongs to another platform");
        }
    }
}
