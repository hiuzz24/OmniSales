package fu.osms.sync.order.pull.impl;

import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.repository.OrderRepository;
import fu.osms.order.service.OrderStockAllocationService;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.importing.OrderImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualOrderPostImportServiceImpl Tests")
class ManualOrderPostImportServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStockAllocationService stockAllocationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ManualOrderPostImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManualOrderPostImportServiceImpl(stockAllocationService, eventPublisher);
    }

    private Order order(UUID id, OrderStatus status) {
        return Order.builder().id(id).status(status).build();
    }

    @Test
    @DisplayName("publish: publishes OrderCreatedEvent when outcome.created = true")
    void publish_created() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.CONFIRMED);
        OrderImportOutcome outcome = new OrderImportOutcome(
                orderId,
                OrderImportResult.CREATED,
                true, false, false, false,
                null, OrderStatus.CONFIRMED);

        when(stockAllocationService.classifyAfterImport(orderId)).thenReturn(order);

        service.publish(outcome);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(OrderCreatedEvent.class);
    }

    @Test
    @DisplayName("publish: publishes OrderPaidEvent when paymentBecamePaid = true")
    void publish_paid() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.CONFIRMED);
        OrderImportOutcome outcome = new OrderImportOutcome(
                orderId,
                OrderImportResult.UPDATED,
                false, true, false, false,
                OrderStatus.PENDING, OrderStatus.CONFIRMED);

        when(stockAllocationService.classifyAfterImport(orderId)).thenReturn(order);

        service.publish(outcome);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(OrderPaidEvent.class);
    }

    @Test
    @DisplayName("publish: publishes OrderCancelledEvent when becameCancelled = true")
    void publish_cancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.CANCELLED);
        OrderImportOutcome outcome = new OrderImportOutcome(
                orderId,
                OrderImportResult.UPDATED,
                false, false, true, false,
                OrderStatus.CONFIRMED, OrderStatus.CANCELLED);

        when(stockAllocationService.classifyAfterImport(orderId)).thenReturn(order);

        service.publish(outcome);

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(OrderCancelledEvent.class);
    }

    @Test
    @DisplayName("publish: publishes OrderStatusChangedEvent when status changed")
    void publish_statusChanged() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.PROCESSING);
        OrderImportOutcome outcome = new OrderImportOutcome(
                orderId,
                OrderImportResult.UPDATED,
                false, false, false, false,
                OrderStatus.CONFIRMED, OrderStatus.PROCESSING);

        when(stockAllocationService.classifyAfterImport(orderId)).thenReturn(order);

        service.publish(outcome);

        ArgumentCaptor<OrderStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("publish: publishes only OrderCreatedEvent when created and paid are both true")
    void publish_createdAndPaid() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId, OrderStatus.CONFIRMED);
        OrderImportOutcome outcome = new OrderImportOutcome(
                orderId,
                OrderImportResult.CREATED,
                true, true, false, false,
                null, OrderStatus.CONFIRMED);

        when(stockAllocationService.classifyAfterImport(orderId)).thenReturn(order);

        service.publish(outcome);

        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        verify(eventPublisher).publishEvent(any(OrderPaidEvent.class));
    }
}
