package fu.osms.sync.order.pull.impl;

import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.event.OrderCancelledEvent;
import fu.osms.order.event.OrderCreatedEvent;
import fu.osms.order.event.OrderPaidEvent;
import fu.osms.order.event.OrderStatusChangedEvent;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.order.importing.OrderImportOutcome;
import fu.osms.sync.order.importing.OrderImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManualOrderPostImportServiceImpl Tests")
class ManualOrderPostImportServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private PlatformOrderInventoryService inventoryService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private ManualOrderPostImportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManualOrderPostImportServiceImpl(orderRepository, inventoryService, eventPublisher);
    }

    private Order order(UUID id) {
        return Order.builder().id(id).build();
    }

    private OrderImportOutcome outcome(UUID orderId,
                                       OrderImportResult result,
                                       boolean created,
                                       boolean paid,
                                       boolean cancelled,
                                       boolean statusChanged) {
        return new OrderImportOutcome(
                orderId,
                result,
                created,
                paid,
                cancelled,
                false,
                statusChanged ? OrderStatus.PENDING : null,
                statusChanged ? OrderStatus.CONFIRMED : null
        );
    }

    @Test
    @DisplayName("publish: throws when order is not found")
    void publish_orderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.publish(outcome(orderId, OrderImportResult.CREATED, true, false, false, false)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("publish: reserves inventory and publishes OrderCreatedEvent when outcome.created = true")
    void publish_created() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.publish(outcome(orderId, OrderImportResult.CREATED, true, false, false, false));

        verify(inventoryService).syncReservations(order);
        verify(eventPublisher).publishEvent(any(OrderCreatedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(OrderPaidEvent.class));
        verify(eventPublisher, never()).publishEvent(any(OrderCancelledEvent.class));
        verify(eventPublisher, never()).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    @DisplayName("publish: publishes OrderPaidEvent when paymentBecamePaid = true")
    void publish_paid() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.publish(outcome(orderId, OrderImportResult.UPDATED, false, true, false, false));

        verify(eventPublisher).publishEvent(any(OrderPaidEvent.class));
        verify(eventPublisher, never()).publishEvent(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("publish: publishes OrderCancelledEvent when becameCancelled = true")
    void publish_cancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.publish(outcome(orderId, OrderImportResult.UPDATED, false, false, true, false));

        verify(eventPublisher).publishEvent(any(OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("publish: publishes OrderStatusChangedEvent when statusChanged() returns true")
    void publish_statusChanged() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.publish(outcome(orderId, OrderImportResult.UPDATED, false, false, false, true));

        verify(eventPublisher).publishEvent(any(OrderStatusChangedEvent.class));
    }

    @Test
    @DisplayName("publish: wraps inventory exceptions in IllegalStateException with [INV] prefix")
    void publish_inventoryError() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        doThrow(new RuntimeException("stock blew up")).when(inventoryService).syncReservations(order);

        assertThatThrownBy(() -> service.publish(outcome(orderId, OrderImportResult.CREATED, true, false, false, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("[INV]")
                .hasMessageContaining("stock blew up");
    }

    @Test
    @DisplayName("publish: no events published for UNCHANGED outcome with no flags set")
    void publish_unchanged() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        service.publish(outcome(orderId, OrderImportResult.UNCHANGED, false, false, false, false));

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("publish: wraps null inventory exception message with the exception class name")
    void publish_inventoryErrorNullMessage() {
        UUID orderId = UUID.randomUUID();
        Order order = order(orderId);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        doThrow(new IllegalArgumentException()).when(inventoryService).syncReservations(order);

        assertThatThrownBy(() -> service.publish(outcome(orderId, OrderImportResult.CREATED, true, false, false, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("[INV]")
                .hasMessageContaining("IllegalArgumentException");
    }
}
