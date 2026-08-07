package fu.osms.orderreturn.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.common.utils.SecurityUtils;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.orderreturn.entity.OrderReturn;
import fu.osms.orderreturn.entity.OrderReturnItem;
import fu.osms.orderreturn.enums.OrderReturnStatus;
import fu.osms.orderreturn.repository.OrderReturnItemRepository;
import fu.osms.orderreturn.repository.OrderReturnRepository;
import fu.osms.orderreturn.service.OrderReturnInventoryPostingService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrderReturnInventoryPostingServiceImpl implements OrderReturnInventoryPostingService {

    private static final String REFERENCE_TYPE = "RECEIPT";

    private final OrderReturnRepository returnRepository;
    private final OrderReturnItemRepository returnItemRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final MarketplaceInventoryPropagationService propagationService;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void postIfReady(UUID returnId) {
        OrderReturn orderReturn = returnRepository.findForUpdateById(returnId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_RETURN_NOT_FOUND));
        if (orderReturn.getInventoryPostedAt() != null
                || transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                REFERENCE_TYPE, returnId, InvTxnType.IMPORT)) {
            return;
        }
        if (orderReturn.getInspectedAt() == null || orderReturn.getRefundConfirmedAt() == null) {
            return;
        }
        if (orderReturn.getPlatform() == PlatformType.SHOPIFY
                && !"CLOSED".equalsIgnoreCase(orderReturn.getPlatformStatus())) {
            return;
        }
        if (orderReturn.getWarehouse() == null) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND, "Return does not have a receiving warehouse");
        }

        List<OrderReturnItem> items = returnItemRepository.findByReturnIdWithDetails(returnId).stream()
                .filter(item -> item.getRestockableQuantity() != null && item.getRestockableQuantity() > 0)
                .sorted(Comparator.comparing(item -> item.getVariant() == null
                        ? new UUID(0, 0) : item.getVariant().getId()))
                .toList();
        Set<UUID> changedVariantIds = new LinkedHashSet<>();
        User actor = SecurityUtils.getCurrentUser().orElse(null);
        for (OrderReturnItem item : items) {
            if (item.getVariant() == null) {
                throw new AppException(ErrorCode.VARIANT_NOT_FOUND,
                        "Return SKU is not linked to an OSMS variant: " + item.getSnapshotSku());
            }
            InventoryItem inventory = inventoryItemRepository.findByWarehouseIdAndVariantIdWithLock(
                            orderReturn.getWarehouse().getId(), item.getVariant().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                            "Missing inventory item warehouse=" + orderReturn.getWarehouse().getId()
                                    + ", sku=" + item.getSnapshotSku()));
            int before = inventory.getQuantityOnHand() == null ? 0 : inventory.getQuantityOnHand();
            int after = before + item.getRestockableQuantity();
            inventory.setQuantityOnHand(after);
            inventory.setUpdatedBy(actor);
            inventoryItemRepository.save(inventory);
            transactionRepository.save(InventoryTransaction.builder()
                    .warehouse(orderReturn.getWarehouse())
                    .variant(item.getVariant())
                    .type(InvTxnType.IMPORT)
                    .referenceType(REFERENCE_TYPE)
                    .referenceId(returnId)
                    .quantityChange(item.getRestockableQuantity())
                    .quantityBefore(before)
                    .quantityAfter(after)
                    .unitCost(item.getSnapshotCostPrice())
                    .performedBy(actor)
                    .performedAt(OffsetDateTime.now())
                    .note("Restock from order return " + orderReturn.getExternalReturnId())
                    .build());
            changedVariantIds.add(item.getVariant().getId());
        }
        orderReturn.setInventoryPostedAt(OffsetDateTime.now());
        orderReturn.setStatus(OrderReturnStatus.COMPLETED);
        orderReturn.setLastSyncError(null);
        returnRepository.save(orderReturn);
        if (!changedVariantIds.isEmpty()) {
            propagationService.schedulePushAvailableStock(changedVariantIds);
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public void markPending(UUID returnId, String error) {
        returnRepository.findForUpdateById(returnId).ifPresent(orderReturn -> {
            if (orderReturn.getInventoryPostedAt() == null) {
                orderReturn.setStatus(OrderReturnStatus.PENDING_STOCK);
                orderReturn.setLastSyncError(error);
                returnRepository.save(orderReturn);
            }
        });
    }
}
