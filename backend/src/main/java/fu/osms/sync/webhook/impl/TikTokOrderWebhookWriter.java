package fu.osms.sync.webhook.impl;

import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TikTokOrderWebhookWriter {
    private final WebhookEventRepository webhookEventRepository;
    private final TikTokOrderMapper mapper;
    private final TikTokOrderPersistenceService persistenceService;
    private final PlatformOrderInventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void write(UUID eventId, Map<String, Object> detail) {
        WebhookEvent event = webhookEventRepository.findWithChannelById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook event not found: " + eventId));
        TikTokOrderWriteContext context = new TikTokOrderWriteContext(
                event.getChannel(), TikTokOrderWriteSource.WEBHOOK, event.getEventType(),
                WebhookPayloadUtils.copyMap(event.getRawPayload().get("data")), epoch(event.getRawPayload().get("timestamp")));
        OrderImportOutcome outcome = persistenceService.write(context, mapper.map(detail));
        if (outcome.result() != fu.osms.sync.order.importing.OrderImportResult.SKIPPED_STALE) {
            Order order = persistenceService.getOrder(outcome);
            inventoryService.syncReservations(order);
            if (outcome.created()) {
                eventPublisher.publishEvent(new OrderCreatedEvent(order));
            }
            if (outcome.paymentBecamePaid()) {
                eventPublisher.publishEvent(new OrderPaidEvent(order));
            }
            if (outcome.becameCancelled()) {
                eventPublisher.publishEvent(new OrderCancelledEvent(order));
            }
            if (outcome.statusChanged()) {
                eventPublisher.publishEvent(new OrderStatusChangedEvent(
                        outcome.orderId(), outcome.previousStatus(), outcome.currentStatus()));
            }
        }
    }

    private Long epoch(Object value) {
        try {
            return value == null ? null : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
