package fu.osms.orderreturn.service.impl;

import fu.osms.common.exception.AppException;
import fu.osms.order.entity.Order;
import fu.osms.orderreturn.dto.request.OrderReturnRejectRequest;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.enums.ReturnAction;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.orderreturn.service.OrderReturnActionService;
import fu.osms.orderreturn.service.OrderReturnInventoryPostingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderReturnServiceImpl Tests")
class OrderReturnServiceImplTest {

    @Mock private OrderReturnRepository returnRepository;
    @Mock private OrderReturnItemRepository itemRepository;
    @Mock private OrderReturnInspectionTransactionService inspectionTransactionService;
    @Mock private OrderReturnActionService actionService;
    @Mock private OrderReturnInventoryPostingService inventoryPostingService;

    private OrderReturnServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderReturnServiceImpl(returnRepository, itemRepository,
                inspectionTransactionService, actionService, inventoryPostingService);
    }

    private OrderReturn orderReturn(UUID id) {
        return OrderReturn.builder()
                .id(id)
                .order(Order.builder().id(UUID.randomUUID()).build())
                .channel(fu.osms.channel.entity.Channel.builder()
                        .id(UUID.randomUUID())
                        .displayName("Shopify Store")
                        .build())
                .build();
    }

    @Test
    @DisplayName("getById: throws AppException when return is not found")
    void getById_notFound() {
        UUID id = UUID.randomUUID();
        when(returnRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("approve: delegates to actionService")
    void approve() {
        UUID id = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(id);
        when(returnRepository.findById(id)).thenReturn(Optional.of(orderReturn));
        when(itemRepository.findByReturnIdWithDetails(id)).thenReturn(List.of());

        service.approve(id);

        verify(actionService).execute(eq(id), eq(ReturnAction.APPROVE), eq(null));
    }

    @Test
    @DisplayName("reject: throws when both legacy reason and reasonCode/comment are sent together")
    void reject_legacyAndStructured() {
        UUID id = UUID.randomUUID();
        OrderReturnRejectRequest request = new OrderReturnRejectRequest("code-1", "customer comment", "legacy reason");

        assertThatThrownBy(() -> service.reject(id, request)).isInstanceOf(AppException.class);
        verify(actionService, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("reject: passes reasonCode as a ReturnRejectCommand to actionService when only structured fields are set")
    void reject_structured() {
        UUID id = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(id);
        OrderReturnRejectRequest request = new OrderReturnRejectRequest("code-1", "customer angry", null);
        when(returnRepository.findById(id)).thenReturn(Optional.of(orderReturn));
        when(itemRepository.findByReturnIdWithDetails(id)).thenReturn(List.of());

        service.reject(id, request);

        verify(actionService).execute(eq(id), eq(ReturnAction.REJECT),
                org.mockito.ArgumentMatchers.argThat(cmd ->
                        cmd != null && "code-1".equals(cmd.reasonCode())
                                && "customer angry".equals(cmd.comment())));
    }

    @Test
    @DisplayName("reject: legacy reason is mapped to comment when only reason is set")
    void reject_legacyToComment() {
        UUID id = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(id);
        OrderReturnRejectRequest request = new OrderReturnRejectRequest(null, null, "legacy-reason");
        when(returnRepository.findById(id)).thenReturn(Optional.of(orderReturn));
        when(itemRepository.findByReturnIdWithDetails(id)).thenReturn(List.of());

        service.reject(id, request);

        verify(actionService).execute(eq(id), eq(ReturnAction.REJECT),
                org.mockito.ArgumentMatchers.argThat(cmd ->
                        cmd != null && "legacy-reason".equals(cmd.comment())));
    }

    @Test
    @DisplayName("retryStock: rethrows the original exception after markPending fails (inventory already posted)")
    void retryStock_markPendingSkipped() {
        UUID id = UUID.randomUUID();
        doThrow(new RuntimeException("inventory boom")).when(inventoryPostingService).postIfReady(id);

        assertThatThrownBy(() -> service.retryStock(id)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("inventory boom");
    }
}