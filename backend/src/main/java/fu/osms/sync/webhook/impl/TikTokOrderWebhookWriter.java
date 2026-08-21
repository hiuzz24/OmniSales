package fu.osms.sync.webhook.impl;

import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.service.OrderStockAllocationService;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.support.OrderStockMetadata;
import fu.osms.order.support.TikTokBuyerCancellationMetadata;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.notification.service.OrderWorkflowNotificationService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.repository.WebhookEventRepository;
import fu.osms.sync.tiktok.order.*;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TikTokOrderWebhookWriter {
    private final WebhookEventRepository webhookEventRepository;
    private final TikTokOrderMapper mapper;
    private final TikTokOrderPersistenceService persistenceService;
    private final OrderStockAllocationService stockAllocationService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PlatformOrderInventoryService inventoryService;
    private final MarketplaceInventoryPropagationService inventoryPropagationService;
    private final OrderWorkflowNotificationService notificationService;

    @Transactional
    public void write(UUID eventId, Map<String, Object> detail) {
        WebhookEvent event = webhookEventRepository.findWithChannelById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook event not found: " + eventId));
        TikTokOrderWriteContext context = new TikTokOrderWriteContext(
                event.getChannel(), TikTokOrderWriteSource.WEBHOOK, event.getEventType(),
                WebhookPayloadUtils.copyMap(event.getRawPayload().get("data")), epoch(event.getRawPayload().get("timestamp")));
        OrderImportOutcome outcome = persistenceService.write(context, mapper.map(detail));
        if (outcome.result() != fu.osms.sync.order.importing.OrderImportResult.SKIPPED_STALE) {
            Order order = stockAllocationService.classifyAfterImport(outcome.orderId());
            if (outcome.created()) {
                eventPublisher.publishEvent(new OrderCreatedEvent(order));
            }
            if (outcome.paymentBecamePaid()) {
                eventPublisher.publishEvent(new OrderPaidEvent(order));
            }
            if (outcome.becameCancelled()) {
                eventPublisher.publishEvent(new OrderCancelledEvent(order));
            }
            if (outcome.previousStatus() != order.getStatus()) {
                eventPublisher.publishEvent(new OrderStatusChangedEvent(
                        outcome.orderId(), outcome.previousStatus(), order.getStatus()));
            }
        }
    }

    /** Gắn cancellation trước khi phân bổ tồn để webhook type 11 đến sớm không reserve nhầm. */
    @Transactional
    public void writeCancellation(UUID eventId, String externalOrderId, Map<String, Object> bootstrapDetail,
                                  TikTokOrderApiService.Cancellation cancellation) {
        WebhookEvent event = webhookEventRepository.findWithChannelById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook event not found: " + eventId));
        OrderImportOutcome outcome = null;
        if (bootstrapDetail != null) {
            TikTokOrderWriteContext context = new TikTokOrderWriteContext(
                    event.getChannel(), TikTokOrderWriteSource.WEBHOOK, event.getEventType(),
                    WebhookPayloadUtils.copyMap(event.getRawPayload().get("data")),
                    epoch(event.getRawPayload().get("timestamp")));
            outcome = persistenceService.write(context, mapper.map(bootstrapDetail));
        }

        Order order = orderRepository.findForUpdateByChannelIdAndExternalOrderId(
                        event.getChannel().getId(), externalOrderId)
                .orElseThrow(() -> new IllegalArgumentException("TikTok order not found: " + externalOrderId));
        OrderStatus previousStatus = outcome == null ? order.getStatus() : outcome.previousStatus();
        Map<String, Object> existing = TikTokBuyerCancellationMetadata.value(order);
        Long storedUpdateTime = longValue(existing.get("lastUpdateTime"));
        String storedCancelId = text(existing.get("cancelId"));
        if (storedUpdateTime != null && cancellation.updateTime() != null
                && cancellation.cancelId().equals(storedCancelId)
                && cancellation.updateTime() <= storedUpdateTime) {
            return;
        }
        if (storedCancelId != null && !cancellation.cancelId().equals(storedCancelId)) {
            existing = new LinkedHashMap<>();
        }

        boolean wasSellerActionRequired = booleanValue(existing.get("sellerActionRequired"));
        Map<String, Object> metadata = new LinkedHashMap<>(existing);
        metadata.put("cancelId", cancellation.cancelId());
        if (metadata.get("initiatorRole") == null) {
            metadata.put("initiatorRole", cancellation.initiatorRole());
        }
        metadata.put("lastActorRole", cancellation.lastActorRole());
        metadata.put("cancelStatus", cancellation.cancelStatus());
        metadata.put("sellerNextAction", cancellation.sellerNextAction());
        boolean active = TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(cancellation.cancelStatus())
                || TikTokBuyerCancellationMetadata.SUCCESS.equalsIgnoreCase(cancellation.cancelStatus());
        boolean sellerActionRequired = TikTokBuyerCancellationMetadata.PENDING.equalsIgnoreCase(cancellation.cancelStatus())
                && "BUYER".equalsIgnoreCase(text(metadata.get("initiatorRole")))
                && "SELLER_RESPOND_CANCEL".equalsIgnoreCase(cancellation.sellerNextAction());
        metadata.put("active", active);
        metadata.put("sellerActionRequired", sellerActionRequired);
        metadata.put("lastUpdateTime", cancellation.updateTime());
        if (metadata.get("actionState") == null) metadata.put("actionState", "IDLE");
        if (metadata.get("lastAction") == null) metadata.put("lastAction", null);
        if (metadata.get("actionRequestId") == null) metadata.put("actionRequestId", null);
        if (metadata.get("lastError") == null) metadata.put("lastError", null);
        if (!active && "PROCESSING".equals(metadata.get("actionState"))) metadata.put("actionState", "RESOLVED");
        TikTokBuyerCancellationMetadata.replace(order, metadata);

        if (TikTokBuyerCancellationMetadata.COMPLETE.equalsIgnoreCase(cancellation.cancelStatus())) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setStatusChangedAt(java.time.OffsetDateTime.now());
            OrderStockMetadata.clearLifecycle(order);
        }
        orderRepository.save(order);

        if (order.getStatus() == OrderStatus.CANCELLED) {
            inventoryService.releaseOrderReservations(order.getId());
        } else if (!active) {
            if (order.getStatus() == OrderStatus.WAITING_STOCK) {
                inventoryPropagationService.scheduleWaitingStockReconcile(variantIds(order.getId()));
            } else if (bootstrapDetail != null) {
                order = stockAllocationService.classifyAfterImport(order.getId());
            }
        }

        if (sellerActionRequired && !wasSellerActionRequired) {
            Order notifyOrder = order;
            afterCommit(() -> notificationService.notifyRolesOnce(
                    List.of("OWNER", "SALES"), "ORDER_BUYER_CANCEL_REQUESTED",
                    "Khách hàng yêu cầu hủy đơn TikTok",
                    "Đơn " + notifyOrder.getExternalOrderId() + " đang chờ duyệt yêu cầu hủy.",
                    "ORDER", notifyOrder.getId()));
        }
        publishImportEvents(outcome, order, previousStatus);
    }

    private void publishImportEvents(OrderImportOutcome outcome, Order order, OrderStatus previousStatus) {
        if (outcome != null && outcome.created()) eventPublisher.publishEvent(new OrderCreatedEvent(order));
        if (outcome != null && outcome.paymentBecamePaid()) eventPublisher.publishEvent(new OrderPaidEvent(order));
        if (previousStatus != OrderStatus.CANCELLED && order.getStatus() == OrderStatus.CANCELLED) {
            eventPublisher.publishEvent(new OrderCancelledEvent(order));
        }
        if (previousStatus != order.getStatus()) {
            eventPublisher.publishEvent(new OrderStatusChangedEvent(order.getId(), previousStatus, order.getStatus()));
        }
    }

    private Set<UUID> variantIds(UUID orderId) {
        return orderItemRepository.findByOrderId(orderId).stream()
                .filter(item -> item.getVariant() != null)
                .map(item -> item.getVariant().getId())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private void afterCommit(Runnable action) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override public void afterCommit() { action.run(); }
                });
    }

    private String text(Object value) { return WebhookPayloadUtils.text(value); }
    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
    private Long longValue(Object value) {
        try { return value == null ? null : Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private Long epoch(Object value) {
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
