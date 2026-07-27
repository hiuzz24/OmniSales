package fu.osms.sync.shopify.order.impl;

import fu.osms.channel.entity.Channel;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.importing.OrderImportResult;
import fu.osms.sync.order.importing.OrderUpsertResult;
import fu.osms.sync.order.importing.OrderUpsertSupport;
import fu.osms.sync.shopify.order.ShopifyOrderPersistenceService;
import fu.osms.sync.shopify.order.ShopifyOrderWriteModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopifyOrderPersistenceServiceImpl implements ShopifyOrderPersistenceService {
    private final OrderUpsertSupport upsertSupport;
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final ChannelProductVariantRepository channelVariantRepository;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OrderImportOutcome write(Channel channel, ShopifyOrderWriteModel model) {
        List<ResolvedItem> items = model.items().stream().map(item -> resolve(channel, item)).toList();
        OrderUpsertResult upsert = upsertSupport.ensureAndLock(channel, model.externalOrderId(), PlatformType.SHOPIFY);
        Order order = upsert.order();
        OrderStatus oldStatus = order.getStatus();
        String oldPayment = order.getPaymentStatus();
        order.setChannel(channel);
        order.setChannelName(channel.getDisplayName());
        order.setPlatform(PlatformType.SHOPIFY);
        if (upsert.created() || oldStatus != model.status()) order.setStatusChangedAt(OffsetDateTime.now());
        order.setStatus(model.status());
        order.setPaymentStatus(model.paymentStatus());
        setText(model.buyerName(), order::setBuyerName);
        setText(model.buyerPhone(), order::setBuyerPhone);
        if (shouldReplaceAddress(order, model.shippingAddress())) order.setShippingAddress(model.shippingAddress());
        order.setSubtotal(model.subtotal());
        order.setDiscountAmount(model.discountAmount());
        order.setShippingFee(model.shippingFee());
        order.setCurrency(model.currency());
        setText(model.note(), order::setNote);
        setText(model.trackingNumber(), order::setTrackingNumber);
        setText(model.cancelReason(), order::setCancelReason);
        Order saved = orderRepository.save(order);
        itemRepository.deleteByOrderId(saved.getId());
        itemRepository.saveAll(items.stream().map(item -> item.entity(saved)).toList());
        return new OrderImportOutcome(saved.getId(), upsert.created() ? OrderImportResult.CREATED : OrderImportResult.UPDATED,
                upsert.created(), "PAID".equals(saved.getPaymentStatus()) && !"PAID".equals(oldPayment),
                saved.getStatus() == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED, false,
                oldStatus, saved.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public Order getOrder(OrderImportOutcome outcome) { return orderRepository.findById(outcome.orderId()).orElseThrow(); }

    private ResolvedItem resolve(Channel channel, ShopifyOrderWriteModel.Item item) {
        ChannelProductVariant mapping = item.externalVariantId() == null ? null : channelVariantRepository
                .findActiveByChannelIdAndExternalVariantId(channel.getId(), item.externalVariantId()).orElse(null);
        return new ResolvedItem(item, mapping);
    }
    private void setText(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank() && !value.contains("***")) setter.accept(value);
    }
    private boolean shouldReplaceAddress(Order order, java.util.Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) return false;
        boolean currentEmpty = order.getShippingAddress() == null || order.getShippingAddress().isEmpty();
        boolean masked = incoming.values().stream().filter(java.util.Objects::nonNull)
                .map(String::valueOf).anyMatch(value -> value.contains("***"));
        return currentEmpty || !masked;
    }
    private record ResolvedItem(ShopifyOrderWriteModel.Item item, ChannelProductVariant mapping) {
        private OrderItem entity(Order order) {
            return OrderItem.builder().order(order).channelVariant(mapping).variant(mapping == null ? null : mapping.getVariant())
                    .sku(item.sku()).name(item.name()).quantity(item.quantity()).unitPrice(item.unitPrice())
                    .discountAmount(item.discountAmount()).costPrice(mapping == null ? null : mapping.getVariant().getCostPrice()).build();
        }
    }
}
