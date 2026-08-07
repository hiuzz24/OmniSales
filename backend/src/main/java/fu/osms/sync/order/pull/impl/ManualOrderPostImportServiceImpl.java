package fu.osms.sync.order.pull.impl;

import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.pull.ManualOrderPostImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManualOrderPostImportServiceImpl implements ManualOrderPostImportService {
    private final OrderRepository orderRepository;
    private final PlatformOrderInventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public void publish(OrderImportOutcome outcome) {
        Order order = orderRepository.findById(outcome.orderId()).orElseThrow();
        reserveInventory(order);
        if (outcome.created()) eventPublisher.publishEvent(new OrderCreatedEvent(order));
        if (outcome.paymentBecamePaid()) eventPublisher.publishEvent(new OrderPaidEvent(order));
        if (outcome.becameCancelled()) eventPublisher.publishEvent(new OrderCancelledEvent(order));
        if (outcome.statusChanged()) {
            eventPublisher.publishEvent(new OrderStatusChangedEvent(
                    outcome.orderId(), outcome.previousStatus(), outcome.currentStatus()));
        }
    }

    private void reserveInventory(Order order) {
        try {
            inventoryService.syncReservations(order);
        } catch (Exception exception) {
            String message = exception.getMessage() == null
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            throw new IllegalStateException("[INV] " + message, exception);
        }
    }
}
