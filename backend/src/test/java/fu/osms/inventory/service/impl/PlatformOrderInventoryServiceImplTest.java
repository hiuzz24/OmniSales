package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.enums.ReservationResult;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformOrderInventoryServiceImpl Tests")
class PlatformOrderInventoryServiceImplTest {

    @Mock private OrderRepository orderRepository;
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
                orderRepository, orderItemRepository, productVariantRepository, inventoryItemRepository,
                inventoryTransactionRepository, inventoryAlertService,
                marketplaceInventoryPropagationService, marketplaceWarehouseConsistencyService);
    }

    @Test
    @DisplayName("tryReserve: returns ALREADY_RESERVED when order has existing ORDER_DEDUCT transactions")
    void tryReserve_alreadyReserved() {
        UUID orderId = UUID.randomUUID();
        Warehouse wh = Warehouse.builder().id(UUID.randomUUID()).build();
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).sku("VAR-001").build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED).externalOrderId("EXT-1").build();
        InventoryTransaction existingDeduct = InventoryTransaction.builder()
                .warehouse(wh)
                .variant(variant)
                .type(InvTxnType.ORDER_DEDUCT)
                .referenceType("ORDER")
                .referenceId(orderId)
                .build();

        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of(existingDeduct));

        var result = service.tryReserve(orderId);

        assertThat(result.result()).isEqualTo(ReservationResult.ALREADY_RESERVED);
    }

    @Test
    @DisplayName("tryReserve: returns VARIANT_MAPPING_MISSING when order items have no variant mapping")
    void tryReserve_variantMappingMissing() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED).externalOrderId("EXT-1").build();
        OrderItem orderItem = OrderItem.builder().id(UUID.randomUUID()).sku("UNKNOWN-SKU").name("Unknown").quantity(2).build();

        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(productVariantRepository.findBySkuAndDeletedAtIsNull("UNKNOWN-SKU")).thenReturn(Optional.empty());
        when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse()).thenReturn(Warehouse.builder().id(UUID.randomUUID()).build());

        var result = service.tryReserve(orderId);

        assertThat(result.result()).isEqualTo(ReservationResult.VARIANT_MAPPING_MISSING);
        assertThat(result.missingItems()).isNotEmpty();
    }

    @Test
    @DisplayName("tryReserve: returns INSUFFICIENT_STOCK when inventory is not enough")
    void tryReserve_insufficientStock() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Warehouse wh = Warehouse.builder().id(warehouseId).build();
        ProductVariant variant = ProductVariant.builder().id(variantId).sku("VAR-001").name("Test").build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED).externalOrderId("EXT-1").build();
        OrderItem orderItem = OrderItem.builder().id(UUID.randomUUID()).variant(variant).sku("VAR-001").name("Test").quantity(100).build();
        InventoryItem inventory = InventoryItem.builder()
                .warehouse(wh).variant(variant).quantityOnHand(10).reservedQuantity(5).averageCost(BigDecimal.ZERO).build();

        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse()).thenReturn(wh);
        when(inventoryItemRepository.findByWarehouseIdAndVariantIdInWithLock(warehouseId, List.of(variantId)))
                .thenReturn(List.of(inventory));

        var result = service.tryReserve(orderId);

        assertThat(result.result()).isEqualTo(ReservationResult.INSUFFICIENT_STOCK);
        assertThat(result.missingItems()).isNotEmpty();
    }

    @Test
    @DisplayName("tryReserve: returns RESERVED when inventory is sufficient")
    void tryReserve_success() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Warehouse wh = Warehouse.builder().id(warehouseId).build();
        ProductVariant variant = ProductVariant.builder().id(variantId).sku("VAR-001").name("Test").build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CONFIRMED).externalOrderId("EXT-1").build();
        OrderItem orderItem = OrderItem.builder().id(UUID.randomUUID()).variant(variant).sku("VAR-001").name("Test").quantity(2).build();
        InventoryItem inventory = InventoryItem.builder()
                .warehouse(wh).variant(variant).quantityOnHand(20).reservedQuantity(1).averageCost(BigDecimal.ZERO).build();

        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId)).thenReturn(List.of());
        when(orderItemRepository.findByOrderId(orderId)).thenReturn(List.of(orderItem));
        when(marketplaceWarehouseConsistencyService.resolveMasterWarehouse()).thenReturn(wh);
        when(inventoryItemRepository.findByWarehouseIdAndVariantIdInWithLock(warehouseId, List.of(variantId)))
                .thenReturn(List.of(inventory));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.tryReserve(orderId);

        assertThat(result.result()).isEqualTo(ReservationResult.RESERVED);
        assertThat(result.changedVariantIds()).contains(variantId);
        verify(inventoryItemRepository).save(any(InventoryItem.class));
        verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        verify(marketplaceInventoryPropagationService).schedulePushAvailableStock(any());
    }

    @Test
    @DisplayName("releaseOrderReservations: returns empty set when order already has ORDER_CANCEL transactions")
    void releaseOrderReservations_alreadyCancelled() {
        UUID orderId = UUID.randomUUID();
        Warehouse wh = Warehouse.builder().id(UUID.randomUUID()).build();
        ProductVariant variant = ProductVariant.builder().id(UUID.randomUUID()).build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CANCELLED).build();
        InventoryTransaction cancelTransaction = InventoryTransaction.builder()
                .warehouse(wh).variant(variant).type(InvTxnType.ORDER_CANCEL)
                .referenceType("ORDER").referenceId(orderId).build();

        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of(cancelTransaction));

        var result = service.releaseOrderReservations(orderId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("releaseOrderReservations: returns changed variant IDs when releasing reservations")
    void releaseOrderReservations_success() {
        UUID orderId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        Warehouse wh = Warehouse.builder().id(warehouseId).build();
        ProductVariant variant = ProductVariant.builder().id(variantId).build();
        Order order = Order.builder().id(orderId).status(OrderStatus.CANCELLED).externalOrderId("EXT-1").build();
        InventoryTransaction deductTransaction = InventoryTransaction.builder()
                .warehouse(wh).variant(variant).type(InvTxnType.ORDER_DEDUCT)
                .referenceType("ORDER").referenceId(orderId).quantityChange(-5)
                .unitCost(BigDecimal.TEN).build();
        InventoryItem inventory = InventoryItem.builder()
                .warehouse(wh).variant(variant).quantityOnHand(20).reservedQuantity(5).averageCost(BigDecimal.TEN).build();

        when(orderRepository.findForUpdateById(orderId)).thenReturn(Optional.of(order));
        when(inventoryTransactionRepository.findByReferenceTypeAndReferenceId("ORDER", orderId))
                .thenReturn(List.of(deductTransaction));
        when(inventoryItemRepository.findByWarehouseIdAndVariantIdInWithLock(warehouseId, List.of(variantId)))
                .thenReturn(List.of(inventory));
        when(inventoryItemRepository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(inventoryTransactionRepository.save(any(InventoryTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.releaseOrderReservations(orderId);

        assertThat(result).contains(variantId);
        verify(inventoryItemRepository).save(any(InventoryItem.class));
        verify(inventoryTransactionRepository).save(any(InventoryTransaction.class));
        verify(marketplaceInventoryPropagationService).schedulePushAvailableStock(any());
    }
}
