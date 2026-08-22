package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.inventory.dto.response.ReservationOutcome;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.enums.ReservationResult;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.dto.response.WaitingStockItemResponse;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.repository.OrderItemRepository;
import fu.osms.order.repository.OrderRepository;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformOrderInventoryServiceImpl implements PlatformOrderInventoryService {

    private static final String ORDER_REFERENCE_TYPE = "ORDER";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryAlertService inventoryAlertService;
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;

    /** Giữ toàn bộ SKU của đơn trong một transaction; không giữ dở một phần. */
    @Override
    @Transactional
    public ReservationOutcome tryReserve(UUID orderId) {
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        if (hasOrderDeductTransactions(orderId)) {
            return ReservationOutcome.of(ReservationResult.ALREADY_RESERVED);
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);
        Map<UUID, Requirement> requirements = new LinkedHashMap<>();
        List<WaitingStockItemResponse> unmapped = new ArrayList<>();
        for (OrderItem item : orderItems) {
            int quantity = safeInt(item.getQuantity());
            if (quantity <= 0) continue;
            ProductVariant variant = resolveVariant(item);
            if (variant == null) {
                unmapped.add(new WaitingStockItemResponse(
                        null, item.getSku(), item.getName(), quantity, 0, quantity));
                continue;
            }
            requirements.merge(variant.getId(), new Requirement(variant, quantity, item),
                    (left, right) -> left.add(right.quantity(), right.sourceItem()));
        }
        if (!unmapped.isEmpty()) {
            return new ReservationOutcome(ReservationResult.VARIANT_MAPPING_MISSING, Set.of(), unmapped);
        }
        if (requirements.isEmpty()) {
            return ReservationOutcome.of(ReservationResult.ALREADY_RESERVED);
        }

        Warehouse warehouse = marketplaceWarehouseConsistencyService.resolveMasterWarehouse();
        List<UUID> variantIds = requirements.keySet().stream()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        List<InventoryItem> lockedItems = inventoryItemRepository
                .findByWarehouseIdAndVariantIdInWithLock(warehouse.getId(), variantIds);
        Map<UUID, InventoryItem> inventoryByVariant = new LinkedHashMap<>();
        lockedItems.forEach(item -> inventoryByVariant.put(item.getVariant().getId(), item));

        List<WaitingStockItemResponse> unavailable = new ArrayList<>();
        for (UUID variantId : variantIds) {
            Requirement requirement = requirements.get(variantId);
            InventoryItem inventory = inventoryByVariant.get(variantId);
            if (inventory == null) unavailable.add(missing(requirement, 0));
        }
        if (!unavailable.isEmpty()) {
            return new ReservationOutcome(ReservationResult.INVENTORY_ITEM_MISSING, Set.of(), unavailable);
        }

        List<WaitingStockItemResponse> insufficient = new ArrayList<>();
        for (UUID variantId : variantIds) {
            Requirement requirement = requirements.get(variantId);
            int available = availableQuantity(inventoryByVariant.get(variantId));
            if (available < requirement.quantity()) insufficient.add(missing(requirement, available));
        }
        if (!insufficient.isEmpty()) {
            return new ReservationOutcome(ReservationResult.INSUFFICIENT_STOCK, Set.of(), insufficient);
        }

        Set<UUID> changedVariantIds = new LinkedHashSet<>();
        for (UUID variantId : variantIds) {
            Requirement requirement = requirements.get(variantId);
            InventoryItem inventory = inventoryByVariant.get(variantId);
            int quantityOnHand = safeInt(inventory.getQuantityOnHand());
            int reservedBefore = safeInt(inventory.getReservedQuantity());
            int reservedAfter = reservedBefore + requirement.quantity();
            inventory.setReservedQuantity(reservedAfter);
            inventoryItemRepository.save(inventory);
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .warehouse(warehouse)
                    .variant(requirement.variant())
                    .type(InvTxnType.ORDER_DEDUCT)
                    .referenceType(ORDER_REFERENCE_TYPE)
                    .referenceId(orderId)
                    .quantityChange(-requirement.quantity())
                    .quantityBefore(quantityOnHand - reservedBefore)
                    .quantityAfter(quantityOnHand - reservedAfter)
                    .unitCost(resolveUnitCost(inventory, requirement.sourceItem()))
                    .note("Platform order reserved: " + order.getExternalOrderId())
                    .build());
            inventoryAlertService.notifyLowStockAfterStockChange(inventory);
            changedVariantIds.add(variantId);
        }
        marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds);
        return new ReservationOutcome(ReservationResult.RESERVED, changedVariantIds, List.of());
    }

    /** Nhả reservation idempotent khi platform xác nhận đơn đã hủy. */
    @Override
    @Transactional
    public Set<UUID> releaseOrderReservations(UUID orderId) {
        Order order = orderRepository.findForUpdateById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order not found: " + orderId));
        List<InventoryTransaction> transactions = inventoryTransactionRepository
                .findByReferenceTypeAndReferenceId(ORDER_REFERENCE_TYPE, orderId);
        if (transactions.stream().anyMatch(value -> value.getType() == InvTxnType.ORDER_CANCEL)) {
            return Set.of();
        }
        List<InventoryTransaction> deductions = transactions.stream()
                .filter(value -> value.getType() == InvTxnType.ORDER_DEDUCT)
                .sorted(Comparator.comparing((InventoryTransaction value) -> value.getWarehouse().getId().toString())
                        .thenComparing(value -> value.getVariant().getId().toString()))
                .toList();
        if (deductions.isEmpty()) return Set.of();

        Map<UUID, InventoryItem> locked = new LinkedHashMap<>();
        Map<UUID, List<InventoryTransaction>> byWarehouse = new LinkedHashMap<>();
        deductions.forEach(value -> byWarehouse
                .computeIfAbsent(value.getWarehouse().getId(), ignored -> new ArrayList<>()).add(value));
        for (Map.Entry<UUID, List<InventoryTransaction>> entry : byWarehouse.entrySet()) {
            List<UUID> variantIds = entry.getValue().stream().map(value -> value.getVariant().getId())
                    .distinct().sorted(Comparator.comparing(UUID::toString)).toList();
            inventoryItemRepository.findByWarehouseIdAndVariantIdInWithLock(entry.getKey(), variantIds)
                    .forEach(item -> locked.put(item.getVariant().getId(), item));
        }

        Set<UUID> changedVariantIds = new LinkedHashSet<>();
        for (InventoryTransaction transaction : deductions) {
            InventoryItem inventory = locked.get(transaction.getVariant().getId());
            if (inventory == null) {
                log.warn("Cannot release reservation orderId={} variantId={}: inventory item missing",
                        orderId, transaction.getVariant().getId());
                continue;
            }
            int releaseQuantity = Math.abs(safeInt(transaction.getQuantityChange()));
            int quantityOnHand = safeInt(inventory.getQuantityOnHand());
            int reservedBefore = safeInt(inventory.getReservedQuantity());
            int actualRelease = Math.min(releaseQuantity, reservedBefore);
            if (actualRelease <= 0) continue;
            int availableBefore = quantityOnHand - reservedBefore;
            inventory.setReservedQuantity(reservedBefore - actualRelease);
            inventoryItemRepository.save(inventory);
            inventoryAlertService.notifyLowStockAfterStockChange(inventory);
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .warehouse(inventory.getWarehouse())
                    .variant(inventory.getVariant())
                    .type(InvTxnType.ORDER_CANCEL)
                    .referenceType(ORDER_REFERENCE_TYPE)
                    .referenceId(orderId)
                    .quantityChange(actualRelease)
                    .quantityBefore(availableBefore)
                    .quantityAfter(availableBefore + actualRelease)
                    .unitCost(transaction.getUnitCost())
                    .note("Platform order cancelled: " + order.getExternalOrderId())
                    .build());
            changedVariantIds.add(inventory.getVariant().getId());
        }
        marketplaceInventoryPropagationService.schedulePushAvailableStock(changedVariantIds);
        return changedVariantIds;
    }

    private boolean hasOrderDeductTransactions(UUID orderId) {
        return inventoryTransactionRepository.findByReferenceTypeAndReferenceId(ORDER_REFERENCE_TYPE, orderId).stream()
                .anyMatch(value -> value.getType() == InvTxnType.ORDER_DEDUCT);
    }

    private ProductVariant resolveVariant(OrderItem item) {
        if (item.getVariant() != null) return item.getVariant();
        if (item.getSku() == null || item.getSku().isBlank()) return null;
        return productVariantRepository.findBySkuAndDeletedAtIsNull(item.getSku()).orElse(null);
    }

    private WaitingStockItemResponse missing(Requirement requirement, int available) {
        return new WaitingStockItemResponse(requirement.variant().getId(), requirement.variant().getSku(),
                requirement.variant().getName(), requirement.quantity(), available,
                Math.max(0, requirement.quantity() - available));
    }

    private BigDecimal resolveUnitCost(InventoryItem inventory, OrderItem item) {
        if (inventory.getAverageCost() != null) return inventory.getAverageCost();
        return item != null && item.getCostPrice() != null ? item.getCostPrice() : BigDecimal.ZERO;
    }

    private int availableQuantity(InventoryItem item) {
        return safeInt(item.getQuantityOnHand()) - safeInt(item.getReservedQuantity());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private record Requirement(ProductVariant variant, int quantity, OrderItem sourceItem) {
        private Requirement add(int extra, OrderItem latestSource) {
            return new Requirement(variant, quantity + extra, sourceItem != null ? sourceItem : latestSource);
        }
    }
}
