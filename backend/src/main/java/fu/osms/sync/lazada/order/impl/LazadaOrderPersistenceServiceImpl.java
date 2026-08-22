package fu.osms.sync.lazada.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.event.OrderPlatformStockConflictEvent;
import fu.osms.order.support.OrderStockMetadata;
import fu.osms.sync.lazada.order.LazadaOrderPersistenceService;
import fu.osms.sync.lazada.order.LazadaOrderWriteModel;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.importing.OrderImportResult;
import fu.osms.sync.order.importing.OrderUpsertResult;
import fu.osms.sync.order.importing.OrderUpsertSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LazadaOrderPersistenceServiceImpl implements LazadaOrderPersistenceService {

    private final OrderUpsertSupport upsertSupport;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ChannelProductVariantRepository channelVariantRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OrderImportOutcome write(Channel channel, LazadaOrderWriteModel model) {
        List<ResolvedItem> items = model.items().stream().map(item -> resolve(channel, item)).toList();
        OrderUpsertResult upsert = upsertSupport.ensureAndLock(channel, model.externalOrderId(), PlatformType.LAZADA);
        Order order = upsert.order();
        OrderStatus oldStatus = order.getStatus();
        String oldPayment = order.getPaymentStatus();

        order.setChannel(channel);
        order.setChannelName(channel.getDisplayName());
        order.setPlatform(PlatformType.LAZADA);
        if (upsert.created() && model.createdAt() != null) order.setCreatedAt(model.createdAt());
        boolean conflict = oldStatus == OrderStatus.WAITING_STOCK && isProgressStatus(model.status());
        OrderStatus guardedStatus = guardedStatus(oldStatus, model.status());
        if (upsert.created() || oldStatus != guardedStatus) order.setStatusChangedAt(OffsetDateTime.now());
        order.setStatus(guardedStatus);
        order.setPaymentStatus(model.paymentStatus());
        setTextIfPresent(model.buyerName(), order::setBuyerName);
        setTextIfPresent(model.buyerPhone(), order::setBuyerPhone);
        if (shouldReplaceAddress(order, model.shippingAddress())) order.setShippingAddress(model.shippingAddress());
        order.setSubtotal(model.subtotal());
        order.setDiscountAmount(model.discountAmount());
        order.setShippingFee(model.shippingFee());
        order.setCurrency(model.currency());
        setTextIfPresent(model.note(), order::setNote);
        setTextIfPresent(model.trackingNumber(), order::setTrackingNumber);
        if (conflict) {
            OrderStockMetadata.markPlatformConflict(order, String.valueOf(model.status()));
            eventPublisher.publishEvent(new OrderPlatformStockConflictEvent(order.getId(), String.valueOf(model.status())));
        }
        if (guardedStatus == OrderStatus.CANCELLED) OrderStockMetadata.clearLifecycle(order);
        Order saved = orderRepository.save(order);

        orderItemRepository.deleteByOrderId(saved.getId());
        orderItemRepository.saveAll(items.stream().map(item -> item.toEntity(saved)).toList());
        return new OrderImportOutcome(saved.getId(), upsert.created() ? OrderImportResult.CREATED : OrderImportResult.UPDATED,
                upsert.created(), "PAID".equals(saved.getPaymentStatus()) && !"PAID".equals(oldPayment),
                saved.getStatus() == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED, false,
                oldStatus, saved.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(OrderImportOutcome outcome) {
        return orderRepository.findById(outcome.orderId()).orElseThrow();
    }

    private ResolvedItem resolve(Channel channel, LazadaOrderWriteModel.Item item) {
        ChannelProductVariant mapping = item.externalVariantId() == null ? null : channelVariantRepository
                .findActiveByChannelIdAndExternalVariantId(channel.getId(), item.externalVariantId()).orElse(null);
        return new ResolvedItem(item, mapping);
    }

    private void setTextIfPresent(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank() && !value.contains("***")) setter.accept(value);
    }

    private boolean shouldReplaceAddress(Order order, java.util.Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) return false;
        boolean currentEmpty = order.getShippingAddress() == null || order.getShippingAddress().isEmpty();
        boolean masked = incoming.values().stream().filter(java.util.Objects::nonNull)
                .map(String::valueOf).anyMatch(value -> value.contains("***"));
        return currentEmpty || !masked;
    }

    private OrderStatus guardedStatus(OrderStatus current, OrderStatus incoming) {
        if (current != OrderStatus.WAITING_STOCK || incoming == null) return incoming;
        return incoming == OrderStatus.CANCELLED ? OrderStatus.CANCELLED : OrderStatus.WAITING_STOCK;
    }

    private boolean isProgressStatus(OrderStatus incoming) {
        return incoming != null && incoming != OrderStatus.PENDING && incoming != OrderStatus.CONFIRMED
                && incoming != OrderStatus.CANCELLED && incoming != OrderStatus.WAITING_STOCK;
    }

    private record ResolvedItem(LazadaOrderWriteModel.Item item, ChannelProductVariant mapping) {
        private OrderItem toEntity(Order order) {
            return OrderItem.builder().order(order).externalItemId(item.externalItemId()).channelVariant(mapping)
                    .variant(mapping == null ? null : mapping.getVariant()).sku(item.sku()).name(item.name())
                    .quantity(item.quantity()).unitPrice(item.unitPrice()).discountAmount(item.discountAmount())
                    .costPrice(mapping == null ? null : mapping.getVariant().getCostPrice()).build();
        }
    }
}
