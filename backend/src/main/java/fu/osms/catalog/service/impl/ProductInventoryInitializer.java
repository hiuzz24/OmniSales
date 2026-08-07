package fu.osms.catalog.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class ProductInventoryInitializer {

    private final WarehouseRepository warehouseRepository;
    private final InventoryItemRepository inventoryItemRepository;

    ProductInventoryInitializer(WarehouseRepository warehouseRepository,
                                InventoryItemRepository inventoryItemRepository) {
        this.warehouseRepository = warehouseRepository;
        this.inventoryItemRepository = inventoryItemRepository;
    }

    void applyCreateCostPriceDefault(ProductVariant variant) {
        variant.setCostPrice(BigDecimal.ZERO);
    }

    void createInitialInventoryItems(List<ProductVariant> variants,
                                     UUID warehouseId,
                                     Integer lowStockThreshold) {
        if (warehouseId == null || variants == null || variants.isEmpty()) {
            return;
        }

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .filter(w -> w.getDeletedAt() == null)
                .filter(w -> Boolean.TRUE.equals(w.getIsActive()))
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));

        List<InventoryItem> itemsToCreate = new ArrayList<>();
        int threshold = lowStockThreshold == null ? 5 : lowStockThreshold;
        for (ProductVariant variant : variants) {
            if (variant.getId() == null) {
                continue;
            }
            boolean exists = inventoryItemRepository
                    .findByWarehouseIdAndVariantId(warehouse.getId(), variant.getId())
                    .isPresent();
            if (exists) {
                continue;
            }

            itemsToCreate.add(InventoryItem.builder()
                    .warehouse(warehouse)
                    .variant(variant)
                    .quantityOnHand(0)
                    .reservedQuantity(0)
                    .averageCost(variant.getCostPrice() == null ? BigDecimal.ZERO : variant.getCostPrice())
                    .lowStockThreshold(threshold)
                    .build());
        }

        if (!itemsToCreate.isEmpty()) {
            inventoryItemRepository.saveAll(itemsToCreate);
        }
    }
}
