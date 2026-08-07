package fu.osms.orderreturn.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderReturnInventoryPostingServiceImpl Tests")
class OrderReturnInventoryPostingServiceImplTest {

    @Mock private OrderReturnRepository returnRepository;
    @Mock private OrderReturnItemRepository returnItemRepository;
    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private MarketplaceInventoryPropagationService propagationService;

    private OrderReturnInventoryPostingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrderReturnInventoryPostingServiceImpl(returnRepository, returnItemRepository,
                inventoryItemRepository, transactionRepository, propagationService);
    }

    private OrderReturn orderReturn(UUID id, Warehouse warehouse) {
        return OrderReturn.builder()
                .id(id)
                .channel(Channel.builder().id(UUID.randomUUID()).platform(PlatformType.LAZADA).build())
                .warehouse(warehouse)
                .inspectedAt(OffsetDateTime.now().minusSeconds(60))
                .refundConfirmedAt(OffsetDateTime.now().minusSeconds(30))
                .externalReturnId("EXT-RT-1")
                .build();
    }

    private OrderReturnItem item(UUID variantId, int restockable) {
        ProductVariant variant = ProductVariant.builder().id(variantId).sku("SKU-1").build();
        return OrderReturnItem.builder()
                .id(UUID.randomUUID())
                .variant(variant)
                .restockableQuantity(restockable)
                .snapshotSku("SKU-1")
                .snapshotCostPrice(BigDecimal.TEN)
                .build();
    }

    @Test
    @DisplayName("postIfReady: throws AppException when return is not found")
    void postIfReady_returnNotFound() {
        UUID id = UUID.randomUUID();
        when(returnRepository.findForUpdateById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.postIfReady(id)).isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("postIfReady: no-op when inventory has already been posted")
    void postIfReady_alreadyPosted() {
        UUID id = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(id, Warehouse.builder().id(UUID.randomUUID()).build());
        orderReturn.setInventoryPostedAt(OffsetDateTime.now());
        when(returnRepository.findForUpdateById(id)).thenReturn(Optional.of(orderReturn));

        service.postIfReady(id);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("postIfReady: no-op when return is missing inspectedAt")
    void postIfReady_notInspected() {
        UUID id = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(id, Warehouse.builder().id(UUID.randomUUID()).build());
        orderReturn.setInspectedAt(null);
        when(returnRepository.findForUpdateById(id)).thenReturn(Optional.of(orderReturn));

        service.postIfReady(id);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("postIfReady: no-op for Shopify when platformStatus is not CLOSED")
    void postIfReady_shopifyNotClosed() {
        UUID id = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(id, Warehouse.builder().id(UUID.randomUUID()).build());
        orderReturn.setPlatform(PlatformType.SHOPIFY);
        orderReturn.setPlatformStatus("OPEN");
        when(returnRepository.findForUpdateById(id)).thenReturn(Optional.of(orderReturn));

        service.postIfReady(id);

        verify(inventoryItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("postIfReady: throws AppException when warehouse is null")
    void postIfReady_noWarehouse() {
        UUID id = UUID.randomUUID();
        OrderReturn orderReturn = orderReturn(id, null);
        when(returnRepository.findForUpdateById(id)).thenReturn(Optional.of(orderReturn));

        assertThatThrownBy(() -> service.postIfReady(id)).isInstanceOf(AppException.class);
    }

    @Test
    @DisplayName("postIfReady: happy path increases inventory, posts transaction, schedules propagation")
    void postIfReady_happyPath() {
        UUID id = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).name("WH-1").build();
        OrderReturn orderReturn = orderReturn(id, warehouse);
        OrderReturnItem retItem = item(UUID.randomUUID(), 3);
        InventoryItem inventory = InventoryItem.builder()
                .id(UUID.randomUUID())
                .warehouse(warehouse)
                .variant(retItem.getVariant())
                .quantityOnHand(10)
                .build();

        when(returnRepository.findForUpdateById(id)).thenReturn(Optional.of(orderReturn));
        when(returnItemRepository.findByReturnIdWithDetails(id)).thenReturn(List.of(retItem));
        when(inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(
                warehouse.getId(), retItem.getVariant().getId()))
                .thenReturn(Optional.of(inventory));

        service.postIfReady(id);

        assertThat(inventory.getQuantityOnHand()).isEqualTo(13);
        verify(inventoryItemRepository).save(inventory);
        ArgumentCaptor<fu.osms.inventory.entity.InventoryTransaction> captor =
                ArgumentCaptor.forClass(fu.osms.inventory.entity.InventoryTransaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantityChange()).isEqualTo(3);
        assertThat(captor.getValue().getReferenceType()).isEqualTo("RECEIPT");
        verify(propagationService).schedulePushAvailableStock(any());
        assertThat(orderReturn.getInventoryPostedAt()).isNotNull();
    }

    @Test
    @DisplayName("postIfReady: skips items with restockableQuantity = 0")
    void postIfReady_skipZeroRestockable() {
        UUID id = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).name("WH-1").build();
        OrderReturn orderReturn = orderReturn(id, warehouse);
        OrderReturnItem zeroItem = item(UUID.randomUUID(), 0);

        when(returnRepository.findForUpdateById(id)).thenReturn(Optional.of(orderReturn));
        when(returnItemRepository.findByReturnIdWithDetails(id)).thenReturn(List.of(zeroItem));

        service.postIfReady(id);

        verify(inventoryItemRepository, never()).save(any());
        verify(propagationService, never()).schedulePushAvailableStock(any());
    }
}