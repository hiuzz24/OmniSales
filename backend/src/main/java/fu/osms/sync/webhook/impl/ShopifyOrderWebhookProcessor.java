package fu.osms.sync.webhook.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
import fu.osms.sync.shopify.order.ShopifyOrderMapper;
import fu.osms.sync.shopify.order.ShopifyOrderPersistenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ShopifyOrderWebhookProcessor implements PlatformOrderWebhookProcessor {
    private final ShopifyOrderMapper mapper;
    private final ShopifyOrderPersistenceService persistenceService;
    private final PlatformOrderInventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public PlatformType getPlatform() { return PlatformType.SHOPIFY; }

    @Override
    @Transactional
    public String process(WebhookEvent event) {
        OrderImportOutcome outcome = persistenceService.write(event.getChannel(),
                mapper.mapWebhook(event.getEventType(), event.getRawPayload()));
        Order order = persistenceService.getOrder(outcome);
        inventoryService.syncReservations(order);
        if (outcome.created()) eventPublisher.publishEvent(new OrderCreatedEvent(order));
        if (outcome.becameCancelled()) eventPublisher.publishEvent(new OrderCancelledEvent(order));
        if (outcome.paymentBecamePaid()) eventPublisher.publishEvent(new OrderPaidEvent(order));
        return "PROCESSED";
    }
}
