package fu.osms.order.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.repository.InventoryItemRepository;
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
    private final OrderItemRepository orderItemRepository;
    private final MarketplaceInventoryPropagationService propagationService;

    ManualOrderInventoryService(ProductVariantRepository productVariantRepository,
                                InventoryItemRepository inventoryItemRepository,
                                OrderItemRepository orderItemRepository,
                                MarketplaceInventoryPropagationService propagationService) {
        this.productVariantRepository = productVariantRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.orderItemRepository = orderItemRepository;
        this.propagationService = propagationService;
    }

    /** Giải phóng reservation của đơn MANUAL cũ khi đơn bị hủy. */
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

    /** Đẩy số lượng available tuyệt đối sau khi giải phóng reservation. */
    void propagate(Set<UUID> changedVariantIds) {
        propagationService.schedulePushAvailableStock(changedVariantIds);
    }

    private ProductVariant resolveVariantBySku(String sku) {
        return sku == null || sku.isBlank()
                ? null : productVariantRepository.findBySkuAndDeletedAtIsNull(sku).orElse(null);
    }

    private int safeInt(Integer value) { return value == null ? 0 : value; }
}
