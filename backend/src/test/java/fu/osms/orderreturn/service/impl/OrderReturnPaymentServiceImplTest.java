package fu.osms.orderreturn.service.impl;

import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.PaymentStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderReturnPaymentServiceImpl Tests")
class OrderReturnPaymentServiceImplTest {

    @Mock private OrderReturnRepository returnRepository;
    @Mock private OrderReturnItemRepository returnItemRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;

    private OrderReturnPaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderReturnPaymentServiceImpl(
                returnRepository, returnItemRepository, orderRepository, orderItemRepository);
    }

    private OrderReturn orderReturn(UUID orderId) {
        return OrderReturn.builder()
                .id(UUID.randomUUID())
                .refundConfirmedAt(java.time.OffsetDateTime.now())
                .order(Order.builder().id(orderId).build())
                .build();
    }

    @Test
    @DisplayName("projectPayment: no-op when return is not found")
    void noReturn() {
        UUID returnId = UUID.randomUUID();
        when(returnRepository.findForUpdateById(returnId)).thenReturn(Optional.empty());

        service.projectPayment(returnId);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("projectPayment: no-op when return exists but refundConfirmedAt is null")
    void noRefundConfirmation() {
        UUID returnId = UUID.randomUUID();
        OrderReturn ret = OrderReturn.builder().id(returnId).refundConfirmedAt(null).build();
        when(returnRepository.findForUpdateById(returnId)).thenReturn(Optional.of(ret));

        service.projectPayment(returnId);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("projectPayment: no-op when the underlying order is not found")
    void noOrder() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderReturn ret = orderReturn(orderId);
        when(returnRepository.findForUpdateById(returnId)).thenReturn(Optional.of(ret));
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.empty());

        service.projectPayment(returnId);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("projectPayment: sets paymentStatus to REFUNDED when all items are fully refunded")
    void fullyRefunded() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        OrderReturn ret = orderReturn(orderId);
        Order order = Order.builder().id(orderId).paymentStatus(PaymentStatus.PAID.name()).build();
        OrderItem orderItem = OrderItem.builder().id(orderItemId).order(order).quantity(2).build();
        OrderReturnItem returnItem = OrderReturnItem.builder()
                .orderItem(orderItem)
                .refundedQuantity(2)
                .orderReturn(ret)
                .build();

        when(returnRepository.findForUpdateById(returnId)).thenReturn(Optional.of(ret));
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(returnItemRepository.findValidItemsByOrderId(orderId)).thenReturn(List.of(returnItem));

        service.projectPayment(returnId);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("projectPayment: leaves paymentStatus untouched when refunds do not cover all items")
    void partiallyRefunded() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        OrderReturn ret = orderReturn(orderId);
        Order order = Order.builder().id(orderId).paymentStatus(PaymentStatus.PAID.name()).build();
        OrderItem orderItem = OrderItem.builder().id(orderItemId).order(order).quantity(5).build();
        OrderReturnItem returnItem = OrderReturnItem.builder()
                .orderItem(orderItem)
                .refundedQuantity(2) // only partial
                .orderReturn(ret)
                .build();

        when(returnRepository.findForUpdateById(returnId)).thenReturn(Optional.of(ret));
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(returnItemRepository.findValidItemsByOrderId(orderId)).thenReturn(List.of(returnItem));

        service.projectPayment(returnId);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("projectPayment: with empty orderItems list, does not flip payment status to REFUNDED")
    void emptyOrderItems() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderReturn ret = orderReturn(orderId);
        Order order = Order.builder().id(orderId).paymentStatus(PaymentStatus.PAID.name()).build();

        when(returnRepository.findForUpdateById(returnId)).thenReturn(Optional.of(ret));
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());
        when(returnItemRepository.findValidItemsByOrderId(orderId)).thenReturn(List.of());

        service.projectPayment(returnId);

        // With empty orderItems, fullyRefunded stays false (initial value)
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID.name());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("projectPayment: skips returnItem entries with null orderItem or null refundedQuantity")
    void skipsInvalidEntries() {
        UUID returnId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        OrderReturn ret = orderReturn(orderId);
        Order order = Order.builder().id(orderId).paymentStatus(PaymentStatus.PAID.name()).build();
        OrderItem orderItem = OrderItem.builder().id(orderItemId).order(order).quantity(1).build();
        OrderReturnItem invalid1 = OrderReturnItem.builder()
                .orderItem(null)
                .refundedQuantity(1)
                .orderReturn(ret)
                .build();
        OrderReturnItem invalid2 = OrderReturnItem.builder()
                .orderItem(orderItem)
                .refundedQuantity(null)
                .orderReturn(ret)
                .build();
        OrderReturnItem valid = OrderReturnItem.builder()
                .orderItem(orderItem)
                .refundedQuantity(1)
                .orderReturn(ret)
                .build();

        when(returnRepository.findForUpdateById(returnId)).thenReturn(Optional.of(ret));
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(returnItemRepository.findValidItemsByOrderId(orderId))
                .thenReturn(List.of(invalid1, invalid2, valid));

        service.projectPayment(returnId);

        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED.name());
    }
}
