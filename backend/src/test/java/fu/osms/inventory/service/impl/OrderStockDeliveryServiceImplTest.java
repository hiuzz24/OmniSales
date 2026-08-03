package fu.osms.inventory.service.impl;

import fu.osms.inventory.dto.response.OrderStockDeliveryCandidateResponse;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.mapper.StockDeliveryMapper;
import fu.osms.inventory.repository.InventoryIssueRepository;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.OrderGiftReservationService;
import fu.osms.order.entity.Order;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderStockDeliveryServiceImpl Tests")
class OrderStockDeliveryServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private InventoryIssueRepository issueRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private UserRepository userRepository;
    @Mock private MarketplaceWarehouseConsistencyService warehouseConsistencyService;
    @Mock private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock private InventoryAlertService inventoryAlertService;
    @Mock private OrderGiftReservationService orderGiftReservationService;
    @Mock private StockDeliveryMapper stockDeliveryMapper;
    @Mock private OrderStockDeliveryBatchService batchService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private OrderStockDeliveryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderStockDeliveryServiceImpl(
                orderRepository, orderItemRepository, issueRepository, inventoryItemRepository,
                transactionRepository, variantRepository, userRepository,
                warehouseConsistencyService, marketplaceInventoryPropagationService,
                inventoryAlertService, orderGiftReservationService, stockDeliveryMapper,
                batchService, eventPublisher);
    }

    @Test
    @DisplayName("getCandidates trims the keyword and queries the repository with PROCESSING status")
    void getCandidates_trimsKeyword() {
        UUID orderId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Order order = Order.builder()
                .id(orderId)
                .status(OrderStatus.PROCESSING)
                .externalOrderId("EXT-001")
                .build();
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findStockDeliveryCandidates(
                eq(OrderStatus.PROCESSING), eq(orderId), eq("my-keyword"), eq(pageable)))
                .thenReturn(page);
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        Page<OrderStockDeliveryCandidateResponse> result = service.getCandidates(orderId, "  my-keyword  ", pageable);

        assertThat(result).hasSize(1);
        assertThat(result.getContent().get(0).orderId()).isEqualTo(orderId);
        assertThat(result.getContent().get(0).externalOrderId()).isEqualTo("EXT-001");
    }

    @Test
    @DisplayName("getCandidates passes null when the keyword is null")
    void getCandidates_nullKeyword() {
        UUID orderId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findStockDeliveryCandidates(
                eq(OrderStatus.PROCESSING), eq(orderId), eq((String) null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<OrderStockDeliveryCandidateResponse> result = service.getCandidates(orderId, null, pageable);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("completeForOrder silently no-ops when the order is missing")
    void completeForOrder_orderMissing() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.empty());

        assertThatCode(() -> service.completeForOrder(orderId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("completeForOrder silently no-ops when the order is not in a shipping state")
    void completeForOrder_wrongOrderStatus() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.PROCESSING).build();
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));

        assertThatCode(() -> service.completeForOrder(orderId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cancelDraftForOrder is a no-op when the order does not exist")
    void cancelDraftForOrder_orderMissing() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.empty());

        assertThatCode(() -> service.cancelDraftForOrder(orderId)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("cancelDraftForOrder is a no-op when the order has no DRAFT issue")
    void cancelDraftForOrder_noDraftIssue() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).build();
        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(issueRepository.findFirstByReferenceIdAndIssueTypeAndStatus(orderId, "ORDER", "DRAFT"))
                .thenReturn(Optional.empty());

        assertThatCode(() -> service.cancelDraftForOrder(orderId)).doesNotThrowAnyException();
    }
}
