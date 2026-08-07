package fu.osms.sync.webhook.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.lazada.order.LazadaOrderApiService;
import fu.osms.sync.lazada.order.LazadaOrderMapper;
import fu.osms.sync.lazada.order.LazadaOrderPersistenceService;
import fu.osms.sync.lazada.order.LazadaOrderStatusContext;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class LazadaOrderWebhookProcessor implements PlatformOrderWebhookProcessor {

    private final LazadaOrderApiService apiService;
    private final LazadaOrderMapper mapper;
    private final LazadaOrderPersistenceService persistenceService;
    private final PlatformOrderInventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.LAZADA;
    }

    @Override
    @Transactional
    public String process(WebhookEvent event) {
        Map<String, Object> payload = event.getRawPayload();
        String orderId = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(payload, "trade_order_id"));
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Lazada webhook payload is missing data.trade_order_id");
        }

        Map<String, Object> detail = apiService.getOrder(event.getChannel(), orderId);
        OrderImportOutcome outcome = persistenceService.write(event.getChannel(), mapper.map(
                LazadaOrderStatusContext.webhook(payload), detail, apiService.getOrderItems(event.getChannel(), orderId)));
        Order order = persistenceService.getOrder(outcome);

        inventoryService.syncReservations(order);
        publishTransitions(outcome, order);
        return "PROCESSED";
    }

    private void publishTransitions(OrderImportOutcome outcome, Order order) {
        if (outcome.created()) eventPublisher.publishEvent(new OrderCreatedEvent(order));
        if (outcome.becameCancelled()) eventPublisher.publishEvent(new OrderCancelledEvent(order));
        if (outcome.paymentBecamePaid()) eventPublisher.publishEvent(new OrderPaidEvent(order));
        if (outcome.statusChanged()) {
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    outcome.orderId(), outcome.previousStatus(), outcome.currentStatus()));
        }
    }
}
