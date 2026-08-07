package fu.osms.order.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.order.dto.request.OrderItemRequest;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ManualOrderInventoryService {

    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryAlertService inventoryAlertService;
    private final OrderItemRepository orderItemRepository;
    private final MarketplaceInventoryPropagationService propagationService;

    ManualOrderInventoryService(ProductVariantRepository productVariantRepository,
                                InventoryItemRepository inventoryItemRepository,
                                InventoryAlertService inventoryAlertService,
                                OrderItemRepository orderItemRepository,
                                MarketplaceInventoryPropagationService propagationService) {
        this.productVariantRepository = productVariantRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryAlertService = inventoryAlertService;
        this.orderItemRepository = orderItemRepository;
        this.propagationService = propagationService;
    }

    ProductVariant resolveVariant(OrderItemRequest request) {
        if (request.getVariantId() != null) {
            return productVariantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
        }
        return request.getSku() == null || request.getSku().isBlank()
                ? null
                : productVariantRepository.findBySkuAndDeletedAtIsNull(request.getSku()).orElse(null);
    }

    boolean reserve(ProductVariant variant, int quantity) {
        List<InventoryItem> inventoryItems = inventoryItemRepository.findByVariantIdWithLock(variant.getId());
        if (inventoryItems.isEmpty()) {
            throw new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                    "SKU " + variant.getSku() + " is not available in any warehouse.");
        }
        int totalAvailable = inventoryItems.stream().mapToInt(this::availableQuantity).sum();
        if (totalAvailable < quantity) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "SKU " + variant.getSku() + " has only " + totalAvailable + " available units.");
        }
        int remaining = quantity;
        List<InventoryItem> changedItems = new ArrayList<>();
        for (InventoryItem item : inventoryItems) {
            if (remaining <= 0) break;
            int reserve = Math.min(availableQuantity(item), remaining);
            if (reserve <= 0) continue;
            item.setReservedQuantity(safeInt(item.getReservedQuantity()) + reserve);
            changedItems.add(item);
            remaining -= reserve;
        }
        inventoryItemRepository.saveAll(changedItems);
        changedItems.forEach(inventoryAlertService::notifyLowStockAfterStockChange);
        return !changedItems.isEmpty();
    }

    Set<UUID> release(Order order) {
        Set<UUID> changedVariantIds = new HashSet<>();
        for (OrderItem orderItem : orderItemRepository.findByOrderId(order.getId())) {
            ProductVariant variant = orderItem.getVariant() != null
                    ? orderItem.getVariant() : resolveVariantBySku(orderItem.getSku());
            if (variant == null) continue;
            int remaining = safeInt(orderItem.getQuantity());
            List<InventoryItem> changedItems = new ArrayList<>();
            for (InventoryItem item : inventoryItemRepository.findByVariantIdWithLock(variant.getId())) {
                if (remaining <= 0) break;
                int release = Math.min(safeInt(item.getReservedQuantity()), remaining);
                if (release <= 0) continue;
                item.setReservedQuantity(safeInt(item.getReservedQuantity()) - release);
                changedItems.add(item);
                remaining -= release;
            }
            if (!changedItems.isEmpty()) {
                inventoryItemRepository.saveAll(changedItems);
                changedVariantIds.add(variant.getId());
            }
        }
        return changedVariantIds;
    }

    void propagate(Set<UUID> changedVariantIds) {
        propagationService.schedulePushAvailableStock(changedVariantIds);
    }

    private ProductVariant resolveVariantBySku(String sku) {
        return sku == null || sku.isBlank()
                ? null : productVariantRepository.findBySkuAndDeletedAtIsNull(sku).orElse(null);
    }

    private int availableQuantity(InventoryItem item) {
        return safeInt(item.getQuantityOnHand()) - safeInt(item.getReservedQuantity());
    }

    private int safeInt(Integer value) { return value == null ? 0 : value; }
}
