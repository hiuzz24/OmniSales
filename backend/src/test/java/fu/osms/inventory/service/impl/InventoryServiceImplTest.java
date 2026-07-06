package fu.osms.inventory.service.impl;

import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.AvailableVariantDTOMapper;
import fu.osms.inventory.mapper.InventoryDetailMapper;
import fu.osms.inventory.mapper.InventoryItemMapper;
import fu.osms.inventory.mapper.InventoryTransactionMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.InventoryAlertService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryServiceImpl Tests")
class InventoryServiceImplTest {

    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private InventoryTransactionRepository transactionRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ProductVariantRepository variantRepository;
    @Mock
    private InventoryItemMapper inventoryItemMapper;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private InventoryDetailMapper inventoryDetailMapper;
    @Mock
    private InventoryTransactionMapper transactionMapper;
    @Mock
    private AvailableVariantDTOMapper availableVariantDTOMapper;
    @Mock
    private InventoryAlertService inventoryAlertService;
    @Mock
    private ChannelProductVariantRepository channelProductVariantRepository;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    // Test data
    private UUID warehouseId;
    private UUID variantId;
    private UUID inventoryItemId;

    private Warehouse warehouse;
    private InventoryItem inventoryItem;
    private InventoryItemResponse itemResponse;

    @BeforeEach
    void setUp() {
        warehouseId = UUID.randomUUID();
        variantId = UUID.randomUUID();
        inventoryItemId = UUID.randomUUID();

        warehouse = Warehouse.builder()
                .id(warehouseId)
                .name("Test Warehouse")
                .isActive(true)
                .build();

        inventoryItem = InventoryItem.builder()
                .id(inventoryItemId)
                .warehouse(warehouse)
                .variant(null)
                .quantityOnHand(100)
                .reservedQuantity(10)
                .availableQuantity(90)
                .lowStockThreshold(5)
                .build();

        itemResponse = InventoryItemResponse.builder()
                .id(inventoryItemId)
                .warehouseId(warehouseId)
                .warehouseName("Test Warehouse")
                .variantId(variantId)
                .variantSku("TEST-VAR-001")
                .variantName("Test Variant")
                .quantityOnHand(100)
                .reservedQuantity(10)
                .availableQuantity(90)
                .lowStockThreshold(5)
                .build();

        // Setup mappers
        lenient().when(inventoryItemMapper.toResponse(any(InventoryItem.class))).thenReturn(itemResponse);
        lenient().when(inventoryItemMapper.toResponseList(anyList())).thenReturn(List.of(itemResponse));
        lenient().when(channelProductVariantRepository.findActiveByVariantIdInWithChannel(anyList()))
                .thenReturn(Collections.emptyList());
    }

    // =========================================================
    // GET INVENTORY TESTS
    // =========================================================

    @Nested
    @DisplayName("getAllInventoryItems() Tests")
    class GetInventoryTests {

        @Test
        @DisplayName("Should return paginated inventory items")
        void shouldReturnPaginatedInventoryItems() {
            Page<InventoryItem> page = new PageImpl<>(List.of(inventoryItem));
            when(inventoryItemRepository.findAllWithVariantRelationshipsFiltered(isNull(), eq(false), any(PageRequest.class))).thenReturn(page);

            PageResponse<InventoryItemResponse> result = inventoryService.getAllInventoryItems(
                    PageRequest.of(0, 10), 0, 10, null, false);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should get items by warehouse")
        void shouldGetItemsByWarehouse() {
            Page<InventoryItem> page = new PageImpl<>(List.of(inventoryItem));
            when(warehouseRepository.existsById(warehouseId)).thenReturn(true);
            when(inventoryItemRepository.findByWarehouseId(eq(warehouseId), any(PageRequest.class))).thenReturn(page);
            when(inventoryItemMapper.toResponse(any(InventoryItem.class))).thenReturn(itemResponse);

            PageResponse<InventoryItemResponse> result = inventoryService.getItems(warehouseId, 0, 10);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should throw when warehouse not found for getItems")
        void shouldThrowWhenWarehouseNotFoundForGetItems() {
            when(warehouseRepository.existsById(warehouseId)).thenReturn(false);

            assertThatThrownBy(() -> inventoryService.getItems(warehouseId, 0, 10))
                    .isInstanceOf(AppException.class);
        }

        @Test
        @DisplayName("Should throw when item not found by warehouse and variant")
        void shouldThrowWhenItemNotFound() {
            when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> inventoryService.getItemByWarehouseAndVariant(warehouseId, variantId))
                    .isInstanceOf(AppException.class);
        }
    }

    // =========================================================
    // LOW STOCK TESTS
    // =========================================================

    @Nested
    @DisplayName("getLowStockItems() Tests")
    class LowStockTests {

        @Test
        @DisplayName("Should return low stock items")
        void shouldReturnLowStockItems() {
            InventoryItem lowStockItem = InventoryItem.builder()
                    .id(UUID.randomUUID())
                    .warehouse(warehouse)
                    .variant(null)
                    .quantityOnHand(3)
                    .reservedQuantity(0)
                    .availableQuantity(3)
                    .lowStockThreshold(5)
                    .build();

            InventoryItemResponse lowStockResponse = InventoryItemResponse.builder()
                    .id(lowStockItem.getId())
                    .quantityOnHand(3)
                    .lowStockThreshold(5)
                    .build();

            when(inventoryItemRepository.findLowStockItems()).thenReturn(List.of(lowStockItem));
            when(inventoryItemMapper.toResponseList(anyList())).thenReturn(List.of(lowStockResponse));

            List<InventoryItemResponse> result = inventoryService.getLowStockItems();

            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty list when no low stock items")
        void shouldReturnEmptyListWhenNoLowStock() {
            when(inventoryItemRepository.findLowStockItems()).thenReturn(Collections.emptyList());
            when(inventoryItemMapper.toResponseList(anyList())).thenReturn(Collections.emptyList());

            List<InventoryItemResponse> result = inventoryService.getLowStockItems();

            assertThat(result).isEmpty();
        }
    }

    // =========================================================
    // UNSUPPORTED OPERATIONS TESTS
    // =========================================================

    @Nested
    @DisplayName("Unsupported Operations Tests")
    class UnsupportedOperationsTests {

        @Test
        @DisplayName("Should throw when createItem is called")
        void shouldThrowWhenCreateItemCalled() {
            assertThatThrownBy(() -> inventoryService.createItem(null))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Should throw when getItemById is called")
        void shouldThrowWhenGetItemByIdCalled() {
            assertThatThrownBy(() -> inventoryService.getItemById(inventoryItemId))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
