package fu.osms.sync.tiktok.order.impl;

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
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.importing.OrderImportResult;
import fu.osms.sync.order.importing.OrderUpsertResult;
import fu.osms.sync.order.importing.OrderUpsertSupport;
import fu.osms.sync.tiktok.order.*;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TikTokOrderPersistenceServiceImpl implements TikTokOrderPersistenceService {
    private final OrderUpsertSupport upsertSupport;
    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final ChannelProductVariantRepository channelVariantRepository;
    private final TikTokOrderMetadataMapper metadataMapper;
    private final TikTokDispatchSlaCalculator slaCalculator;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Lưu hoặc cập nhật order TikTok sau khi webhook hoặc manual pull đã lấy đủ dữ liệu.
     * Hàm cũng bảo vệ trạng thái WAITING_STOCK và cập nhật các OrderItem tương ứng.
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public OrderImportOutcome write(TikTokOrderWriteContext context, TikTokOrderWriteModel model) {
        Channel channel = context.channel();
        List<ResolvedItem> items = model.items().stream().map(item -> resolve(channel, item)).toList();
        OrderUpsertResult upsert = upsertSupport.ensureAndLock(channel, model.externalOrderId(), PlatformType.TIKTOK);
        Order order = upsert.order();
        Map<String, Object> currentTikTok = WebhookPayloadUtils.copyMap(
                (order.getPlatformMetadata() == null ? Map.<String, Object>of() : order.getPlatformMetadata()).get("tiktok"));
        Long storedTime = epoch(currentTikTok.get("lastOrderUpdateTime"));
        boolean stale = model.updateTime() != null && storedTime != null && model.updateTime() < storedTime;
        if (stale) {
            boolean metadataUpdated = context.source() == TikTokOrderWriteSource.WEBHOOK && !context.reverseData().isEmpty();
            if (metadataUpdated) {
                order.setPlatformMetadata(metadataMapper.merge(order.getPlatformMetadata(), context, model, false));
                orderRepository.save(order);
            }
            return new OrderImportOutcome(order.getId(), OrderImportResult.SKIPPED_STALE,
                    upsert.created(), false, false, metadataUpdated, order.getStatus(), order.getStatus());
        }

        OrderStatus oldStatus = order.getStatus();
        String oldPayment = order.getPaymentStatus();
        order.setChannel(channel);
        order.setChannelName(channel.getDisplayName());
        order.setPlatform(PlatformType.TIKTOK);
        if (upsert.created() && model.createdAt() != null) order.setCreatedAt(model.createdAt());
        OrderStatus incomingStatus = model.status();
        boolean conflict = oldStatus == OrderStatus.WAITING_STOCK && isProgressStatus(incomingStatus);
        OrderStatus guardedStatus = guardedStatus(oldStatus, incomingStatus);
        if (guardedStatus != null && (upsert.created() || oldStatus != guardedStatus)) order.setStatusChangedAt(OffsetDateTime.now());
        if (guardedStatus != null) order.setStatus(guardedStatus);
        order.setPaymentStatus(model.paymentStatus());
        setText(model.buyerName(), order::setBuyerName);
        setText(model.buyerPhone(), order::setBuyerPhone);
        if (shouldReplaceAddress(order, model.shippingAddress())) order.setShippingAddress(model.shippingAddress());
        order.setSubtotal(model.subtotal());
        order.setDiscountAmount(model.discountAmount());
        order.setShippingFee(model.shippingFee());
        order.setCurrency(model.currency());
        setText(model.note(), order::setNote);
        setText(model.cancelReason(), order::setCancelReason);
        setText(model.trackingNumber(), order::setTrackingNumber);
        order.setShippingDueTime(model.shippingDueTime());
        order.setCollectionDueTime(model.collectionDueTime());
        order.setPlatformMetadata(metadataMapper.merge(order.getPlatformMetadata(), context, model, true));
        if ("AWAITING_SHIPMENT".equalsIgnoreCase(model.rawStatus())) {
            TikTokDispatchSlaCalculator.SlaWindow window = slaCalculator.calculate(model, OffsetDateTime.now());
            order.setDispatchSlaAt(window.dispatchSlaAt());
            order.setWaitingStockExpiresAt(window.allocationCutoffAt());
        }
        if (conflict) {
            OrderStockMetadata.markPlatformConflict(order, model.rawStatus());
            eventPublisher.publishEvent(new OrderPlatformStockConflictEvent(order.getId(), model.rawStatus()));
        }
        if (guardedStatus == OrderStatus.CANCELLED) OrderStockMetadata.clearLifecycle(order);
        Order saved = orderRepository.save(order);
        itemRepository.deleteByOrderId(saved.getId());
        itemRepository.saveAll(items.stream().map(item -> item.entity(saved)).toList());
        return new OrderImportOutcome(saved.getId(), upsert.created() ? OrderImportResult.CREATED : OrderImportResult.UPDATED,
                upsert.created(), "PAID".equals(saved.getPaymentStatus()) && !"PAID".equals(oldPayment),
                saved.getStatus() == OrderStatus.CANCELLED && oldStatus != OrderStatus.CANCELLED, true,
                oldStatus, saved.getStatus());
    }

    /** Lấy lại order vừa import để các bước xử lý sau tiếp tục dùng entity mới nhất. */
    @Override
    @Transactional(readOnly = true)
    public Order getOrder(OrderImportOutcome outcome) { return orderRepository.findById(outcome.orderId()).orElseThrow(); }

    /**
     * Liên kết item TikTok với variant nội bộ.
     * Ưu tiên external variant ID, sau đó fallback sang SKU của TikTok.
     */
    private ResolvedItem resolve(Channel channel, TikTokOrderWriteModel.Item item) {
        ChannelProductVariant mapping = null;
        if (item.externalVariantId() != null && !item.externalVariantId().isBlank()) {
            mapping = channelVariantRepository.findActiveByChannelIdAndExternalVariantId(channel.getId(), item.externalVariantId()).orElse(null);
        }
        if (mapping == null && item.sku() != null && !item.sku().isBlank()) {
            mapping = channelVariantRepository.findActiveByChannelIdAndExternalSku(channel.getId(), item.sku()).orElse(null);
        }
        return new ResolvedItem(item, mapping);
    }

    /** Chỉ ghi đè thông tin text hợp lệ, không ghi dữ liệu rỗng hoặc đã bị che. */
    private void setText(String value, java.util.function.Consumer<String> setter) {
        if (value != null && !value.isBlank() && !value.contains("***")) setter.accept(value);
    }

    /** Quyết định có nên thay địa chỉ hiện tại bằng địa chỉ mới từ TikTok hay không. */
    private boolean shouldReplaceAddress(Order order, Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) return false;
        boolean currentEmpty = order.getShippingAddress() == null || order.getShippingAddress().isEmpty();
        boolean masked = incoming.values().stream().filter(java.util.Objects::nonNull)
                .map(String::valueOf).anyMatch(value -> value.contains("***"));
        return currentEmpty || !masked;
    }

    /** Chuyển giá trị thời gian từ payload TikTok sang epoch an toàn. */
    private Long epoch(Object value) { try { return value == null ? null : Long.parseLong(String.valueOf(value)); } catch (NumberFormatException e) { return null; } }

    /** Giữ WAITING_STOCK cho đến khi OSMS xử lý tồn; chỉ cho CANCELLED ghi đè. */
    private OrderStatus guardedStatus(OrderStatus current, OrderStatus incoming) {
        if (current != OrderStatus.WAITING_STOCK || incoming == null) return incoming;
        return incoming == OrderStatus.CANCELLED ? OrderStatus.CANCELLED : OrderStatus.WAITING_STOCK;
    }

    /** Xác định incoming status có phải là trạng thái platform đã tiến lên hay không. */
    private boolean isProgressStatus(OrderStatus incoming) {
        return incoming != null && incoming != OrderStatus.PENDING && incoming != OrderStatus.CONFIRMED
                && incoming != OrderStatus.CANCELLED && incoming != OrderStatus.WAITING_STOCK;
    }

    /** Dữ liệu item TikTok sau khi đã tìm thấy mapping variant nội bộ nếu có. */
    private record ResolvedItem(TikTokOrderWriteModel.Item item, ChannelProductVariant mapping) {
        /** Chuyển item đã resolve thành entity OrderItem để lưu vào database. */
        private OrderItem entity(Order order) {
            return OrderItem.builder().order(order).externalItemId(item.externalItemId())
                    .channelVariant(mapping).variant(mapping == null ? null : mapping.getVariant())
                    .sku(item.sku()).name(item.name()).quantity(item.quantity()).unitPrice(item.unitPrice())
                    .discountAmount(item.discountAmount()).costPrice(mapping == null ? null : mapping.getVariant().getCostPrice()).build();
        }
    }
}
