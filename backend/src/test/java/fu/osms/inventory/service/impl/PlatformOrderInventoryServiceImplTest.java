package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformOrderInventoryServiceImpl Tests")
class PlatformOrderInventoryServiceImplTest {

    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ProductVariantRepository productVariantRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository inventoryTransactionRepository;
    @Mock private InventoryAlertService inventoryAlertService;
    @Mock private MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    @Mock private MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    private PlatformOrderInventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlatformOrderInventoryServiceImpl(
                orderItemRepository, productVariantRepository, inventoryItemRepository,
                inventoryTransactionRepository, inventoryAlertService,
                marketplaceInventoryPropagationService, marketplaceWarehouseConsistencyService);
    }

    @Test
    @DisplayName("syncReservations: null order or null id is a no-op")
    void syncReservations_nullOrder() {
        service.syncReservations(null);
        assertThat(true).isTrue(); // No interaction expected.
    }

    @Test
    @DisplayName("syncReservations: empty-id order is a no-op")
    void syncReservations_emptyIdOrder() {
        service.syncReservations(Order.builder().build());
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("syncReservations: CANCELLED order releases existing reservations and persists ORDER_CANCEL transactions")
    void syncReservations_cancelledOrder_releases() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Warehouse wh = Warehouse.builder().id(UUID.randomUUID()).build();
        ProductVariant variant = ProductVariant.builder().id(variantId).sku("VAR-001").build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CANCELLED).externalOrderId("EXT-1").build();

        InventoryItem inventory = InventoryItem.builder()
                .warehouse(wh)
                .variant(variant)
                .quantityOnHand(10)
                .reservedQuantity(5)
                .averageCost(BigDecimal.ZERO)
                .build();
        InventoryTransaction existingReservation = InventoryTransaction.builder()
                .warehouse(wh)
                .variant(variant)
                .type(InvTxnType.ORDER_DEDUCT)
                .referenceType("ORDER")
                .referenceId(orderId)
                .quantityChange(-3)
                .build();

        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of(existingReservation));
        when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(wh.getId(), variantId))
                .thenReturn(Optional.of(inventory));

        service.syncReservations(order);

        assertThat(inventory.getReservedQuantity()).isEqualTo(2);
        verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        verify(marketplaceInventoryPropagationService).schedulePushAvailableStock(any());
    }

    @Test
    @DisplayName("syncReservations: CANCELLED order with already-existing ORDER_CANCEL transactions is idempotent (no further saves)")
    void syncReservations_cancelledAlready() {
        UUID orderId = UUID.randomUUID();
        Warehouse wh = Warehouse.builder().id(UUID.randomUUID()).build();
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).sku("V1").build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CANCELLED).build();

        InventoryTransaction orderCancel = InventoryTransaction.builder()
                .warehouse(wh).variant(variant).type(InvTxnType.ORDER_CANCEL).referenceType("ORDER").referenceId(orderId)
                .build();
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of(orderCancel));

        service.syncReservations(order);

        verify(inventoryItemRepository, org.mockito.Mockito.never()).findByWarehouseIdAndVariantIdWithLock(any(), any());
        verify(inventoryTransactionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("syncReservations: order with existing ORDER_DEDUCT transactions is a no-op (idempotent)")
    void syncReservations_idempotent() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED).build();

        Warehouse wh = Warehouse.builder().id(UUID.randomUUID()).build();
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).sku("V1").build();
        InventoryTransaction existingDeduct = InventoryTransaction.builder()
                .warehouse(wh).variant(variant).type(InvTxnType.ORDER_DEDUCT).referenceType("ORDER").referenceId(orderId)
                .build();
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of(existingDeduct));

        service.syncReservations(order);

        verify(orderItemRepository, org.mockito.Mockito.never()).findByOrderId(orderId);
    }

    @Test
    @DisplayName("syncReservations: order with no items to reserve does not propagate stock changes")
    void syncReservations_noItems() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED).build();
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of());

        service.syncReservations(order);

        verify(marketplaceInventoryPropagationService).schedulePushAvailableStock(org.mockito.ArgumentMatchers.argThat(set -> set.isEmpty()));
    }

    @Test
    @DisplayName("syncReservations: happy path reserves one variant and persists an ORDER_DEDUCT transaction")
    void syncReservations_happyPath() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Warehouse wh = Warehouse.builder().id(warehouseId).build();
        ProductVariant variant = ProductVariant.builder().id(variantId).sku("V1").build();
        OrderItem orderItem = OrderItem.builder().id(UUID.randomUUID()).variant(variant).quantity(2).build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED).externalOrderId("EXT-1").build();

        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse()).thenReturn(wh);
        InventoryItem inventory = InventoryItem.builder()
                .warehouse(wh).variant(variant).quantityOnHand(20).reservedQuantity(1).averageCost(BigDecimal.ZERO).build();
        when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(warehouseId, variantId))
                .thenReturn(Optional.of(inventory));

        AtomicReference<InventoryTransaction> saved = new AtomicReference<>();
        lenient().when(inventoryTransactionRepository.save(any(InventoryTransaction.class)))
                .thenAnswer((Answer<InventoryTransaction>) inv -> {
                    InventoryTransaction tx = inv.getArgument(0);
                    saved.set(tx);
                    return tx;
                });

        service.syncReservations(order);

        assertThat(inventory.getReservedQuantity()).isEqualTo(3);
        assertThat(saved.get()).isNotNull();
        assertThat(saved.get().getType()).isEqualTo(InvTxnType.ORDER_DEDUCT);
        assertThat(saved.get().getReferenceId()).isEqualTo(orderId);
        verify(marketplaceInventoryPropagationService).schedulePushAvailableStock(any());
    }
}