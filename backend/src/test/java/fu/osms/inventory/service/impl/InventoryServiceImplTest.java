package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.mapper.AvailableVariantDTOMapper;
import fu.osms.inventory.mapper.InventoryItemMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryServiceImpl - Unit Tests (selected methods)")
class InventoryServiceImplTest {

    @Mock private InventoryItemRepository inventoryItemRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryItemMapper inventoryItemMapper;
    @Mock private AvailableVariantDTOMapper availableVariantDTOMapper;

    @InjectMocks
    private InventoryServiceImpl inventoryService;

    private Warehouse warehouse;
    private ProductVariant variantA;
    private ProductVariant variantB;

    @BeforeEach
    void setUp() {
        warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho HN").isActive(true).build();
        variantA = ProductVariant.builder().id(UUID.randomUUID()).sku("SKU-A").build();
        variantB = ProductVariant.builder().id(UUID.randomUUID()).sku("SKU-B").build();
    }

    @Test
    @DisplayName("getStockSummary - returns empty map when variantIds is null")
    void getStockSummary_nullReturnsEmpty() {
        Map<UUID, StockSummaryDTO> result = inventoryService.getStockSummary(null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getStockSummary - returns empty map when variantIds is empty")
    void getStockSummary_emptyReturnsEmpty() {
        Map<UUID, StockSummaryDTO> result = inventoryService.getStockSummary(Collections.emptyList());
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getStockSummary - aggregates available/onHand/reserved across warehouses per variant")
    void getStockSummary_aggregatesAcrossWarehouses() {
        Warehouse otherWarehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho HCM").build();
        InventoryItem a1 = InventoryItem.builder().variant(variantA).warehouse(warehouse)
                .quantityOnHand(20).reservedQuantity(5).availableQuantity(15).build();
        InventoryItem a2 = InventoryItem.builder().variant(variantA).warehouse(otherWarehouse)
                .quantityOnHand(10).reservedQuantity(2).availableQuantity(8).build();
        InventoryItem b1 = InventoryItem.builder().variant(variantB).warehouse(warehouse)
                .quantityOnHand(5).reservedQuantity(1).availableQuantity(4).build();

        when(inventoryItemRepository.findByVariantIdIn(any(Collection.class)))
                .thenReturn(List.of(a1, a2, b1));

        Map<UUID, StockSummaryDTO> result = inventoryService.getStockSummary(
                List.of(variantA.getId(), variantB.getId()));

        assertThat(result).hasSize(2);
        StockSummaryDTO aSummary = result.get(variantA.getId());
        assertThat(aSummary.getQuantityOnHand()).isEqualTo(30);
        assertThat(aSummary.getReservedQuantity()).isEqualTo(7);
        assertThat(aSummary.getAvailableQuantity()).isEqualTo(23);

        StockSummaryDTO bSummary = result.get(variantB.getId());
        assertThat(bSummary.getQuantityOnHand()).isEqualTo(5);
        assertThat(bSummary.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("getStockSummary - handles null quantity fields as zero")
    void getStockSummary_nullFieldsAreZero() {
        InventoryItem a1 = InventoryItem.builder().variant(variantA).warehouse(warehouse)
                .quantityOnHand(null).reservedQuantity(null).availableQuantity(null).build();
        when(inventoryItemRepository.findByVariantIdIn(any(Collection.class))).thenReturn(List.of(a1));

        Map<UUID, StockSummaryDTO> result = inventoryService.getStockSummary(List.of(variantA.getId()));

        StockSummaryDTO summary = result.get(variantA.getId());
        assertThat(summary.getQuantityOnHand()).isZero();
        assertThat(summary.getReservedQuantity()).isZero();
        assertThat(summary.getAvailableQuantity()).isZero();
    }

    @Test
    @DisplayName("aggregateSharedSkuStock - keeps reserved when mirrored marketplace variants share stock")
    void aggregateSharedSkuStock_keepsReservedForMirroredVariants() throws Exception {
        UUID warehouseId = UUID.randomUUID();
        InventoryItemResponse shopifyRow = InventoryItemResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .variantId(UUID.randomUUID())
                .quantityOnHand(4)
                .reservedQuantity(3)
                .availableQuantity(1)
                .build();
        InventoryItemResponse tiktokRow = InventoryItemResponse.builder()
                .id(UUID.randomUUID())
                .warehouseId(warehouseId)
                .variantId(UUID.randomUUID())
                .quantityOnHand(4)
                .reservedQuantity(0)
                .availableQuantity(4)
                .build();

        Object totals = ReflectionTestUtils.invokeMethod(
                inventoryService,
                "aggregateSharedSkuStock",
                List.of(shopifyRow, tiktokRow)
        );

        assertThat(recordInt(totals, "quantityOnHand")).isEqualTo(4);
        assertThat(recordInt(totals, "reservedQuantity")).isEqualTo(3);
        assertThat(recordInt(totals, "availableQuantity")).isEqualTo(1);
    }

    @Test
    @DisplayName("getItems - throws WAREHOUSE_NOT_FOUND when warehouse missing")
    void getItems_missingWarehouseThrows() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.existsById(warehouseId)).thenReturn(false);

        assertThatThrownBy(() -> inventoryService.getItems(warehouseId, 0, 10))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.WAREHOUSE_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("getItemByWarehouseAndVariant - throws INVENTORY_ITEM_NOT_FOUND")
    void getItemByWarehouseAndVariant_notFoundThrows() {
        UUID warehouseId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        when(inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId))
                .thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> inventoryService.getItemByWarehouseAndVariant(warehouseId, variantId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining(ErrorCode.INVENTORY_ITEM_NOT_FOUND.getMessage());
    }

    private int recordInt(Object record, String methodName) throws Exception {
        Method method = record.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (int) method.invoke(record);
    }
}
