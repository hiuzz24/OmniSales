package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.PlatformOrderInventoryService;
import fu.osms.order.entity.Order;
import fu.osms.order.entity.OrderItem;
import fu.osms.order.enums.OrderStatus;
import fu.osms.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformOrderInventoryServiceImpl implements PlatformOrderInventoryService {

    private static final String ORDER_REFERENCE_TYPE = "ORDER";

    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryAlertService inventoryAlertService;

    @Override
    @Transactional
    public void syncReservations(Order order) {
        if (order == null || order.getId() == null) {
            return;
        }

        if (order.getStatus() == OrderStatus.CANCELLED) {
            releaseOrderReservations(order);
            return;
        }

        if (hasOrderDeductTransactions(order.getId())) {
            log.debug("Skipping inventory reservation for order {} because it was already reserved", order.getId());
            return;
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem orderItem : orderItems) {
            ProductVariant variant = resolveVariant(orderItem);
            if (variant == null) {
                log.warn("Skipping inventory reservation for order {} item {} because no local variant mapping was found",
                        order.getId(), orderItem.getId());
                continue;
            }
            reserveVariant(order, orderItem, variant);
        }
    }

    private void reserveVariant(Order order, OrderItem orderItem, ProductVariant variant) {
        int quantity = safeInt(orderItem.getQuantity());
        if (quantity <= 0) {
            return;
        }

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
        for (InventoryItem inventoryItem : inventoryItems) {
            if (remaining <= 0) {
                break;
            }

            int reserveQuantity = Math.min(availableQuantity(inventoryItem), remaining);
            if (reserveQuantity <= 0) {
                continue;
            }

            int quantityOnHand = safeInt(inventoryItem.getQuantityOnHand());
            int reservedBefore = safeInt(inventoryItem.getReservedQuantity());
            int availableBefore = quantityOnHand - reservedBefore;
            int reservedAfter = reservedBefore + reserveQuantity;
            int availableAfter = quantityOnHand - reservedAfter;

            inventoryItem.setReservedQuantity(reservedAfter);
            changedItems.add(inventoryItem);

            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .warehouse(inventoryItem.getWarehouse())
                    .variant(variant)
                    .type(InvTxnType.ORDER_DEDUCT)
                    .referenceType(ORDER_REFERENCE_TYPE)
                    .referenceId(order.getId())
                    .quantityChange(-reserveQuantity)
                    .quantityBefore(availableBefore)
                    .quantityAfter(availableAfter)
                    .unitCost(resolveUnitCost(inventoryItem, orderItem))
                    .note("Platform order reserved: " + order.getExternalOrderId())
                    .build());

            remaining -= reserveQuantity;
        }

        inventoryItemRepository.saveAll(changedItems);
        changedItems.forEach(inventoryAlertService::notifyLowStockAfterStockChange);
    }

    private void releaseOrderReservations(Order order) {
        List<InventoryTransaction> transactions = inventoryTransactionRepository
                .findByReferenceTypeAndReferenceId(ORDER_REFERENCE_TYPE, order.getId());
        boolean alreadyCancelled = transactions.stream()
                .anyMatch(transaction -> transaction.getType() == InvTxnType.ORDER_CANCEL);
        if (alreadyCancelled) {
            return;
        }

        transactions.stream()
                .filter(transaction -> transaction.getType() == InvTxnType.ORDER_DEDUCT)
                .forEach(transaction -> {
                    InventoryItem inventoryItem = inventoryItemRepository
                            .findByWarehouseIdAndVariantIdWithLock(
                                    transaction.getWarehouse().getId(),
                                    transaction.getVariant().getId())
                            .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

                    int releaseQuantity = Math.abs(safeInt(transaction.getQuantityChange()));
                    int quantityOnHand = safeInt(inventoryItem.getQuantityOnHand());
                    int reservedBefore = safeInt(inventoryItem.getReservedQuantity());
                    int actualReleaseQuantity = Math.min(releaseQuantity, reservedBefore);
                    if (actualReleaseQuantity <= 0) {
                        return;
                    }

                    int availableBefore = quantityOnHand - reservedBefore;
                    int reservedAfter = reservedBefore - actualReleaseQuantity;
                    int availableAfter = quantityOnHand - reservedAfter;

                    inventoryItem.setReservedQuantity(reservedAfter);
                    inventoryItemRepository.save(inventoryItem);
                    inventoryAlertService.notifyLowStockAfterStockChange(inventoryItem);

                    inventoryTransactionRepository.save(InventoryTransaction.builder()
                            .warehouse(inventoryItem.getWarehouse())
                            .variant(inventoryItem.getVariant())
                            .type(InvTxnType.ORDER_CANCEL)
                            .referenceType(ORDER_REFERENCE_TYPE)
                            .referenceId(order.getId())
                            .quantityChange(actualReleaseQuantity)
                            .quantityBefore(availableBefore)
                            .quantityAfter(availableAfter)
                            .unitCost(transaction.getUnitCost())
                            .note("Platform order cancelled: " + order.getExternalOrderId())
                            .build());
                });
    }

    private boolean hasOrderDeductTransactions(UUID orderId) {
        return inventoryTransactionRepository.findByReferenceTypeAndReferenceId(ORDER_REFERENCE_TYPE, orderId).stream()
                .anyMatch(transaction -> transaction.getType() == InvTxnType.ORDER_DEDUCT);
    }

    private ProductVariant resolveVariant(OrderItem orderItem) {
        if (orderItem.getVariant() != null) {
            return orderItem.getVariant();
        }
        String sku = orderItem.getSku();
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return productVariantRepository.findBySkuAndDeletedAtIsNull(sku).orElse(null);
    }

    private BigDecimal resolveUnitCost(InventoryItem inventoryItem, OrderItem orderItem) {
        if (inventoryItem.getAverageCost() != null) {
            return inventoryItem.getAverageCost();
        }
        if (orderItem.getCostPrice() != null) {
            return orderItem.getCostPrice();
        }
        return BigDecimal.ZERO;
    }

    private int availableQuantity(InventoryItem item) {
        return safeInt(item.getQuantityOnHand()) - safeInt(item.getReservedQuantity());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
