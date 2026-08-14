package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.entity.InventoryIssue;
import fu.osms.inventory.entity.InventoryIssueItem;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.OrderGiftReservationService;
import fu.osms.inventory.service.impl.OrderGiftSharedStockSupport.GiftGroup;
import fu.osms.inventory.service.impl.OrderGiftSharedStockSupport.TransactionGroup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderGiftReservationServiceImpl implements OrderGiftReservationService {

    private static final String GIFT_REFERENCE = "ISSUE_GIFT";
    private static final String ISSUE_REFERENCE = "ISSUE";
    private static final String GIFT_OUTBOUND_NOTE = "Order gift committed";

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryAlertService inventoryAlertService;
    private final OrderGiftSharedStockSupport sharedStockSupport;

    /** Giữ số lượng quà tặng tách biệt với reservation của hàng trong đơn. */
    @Override
    @Transactional
    public Set<UUID> reserveGiftReservations(
            InventoryIssue issue,
            Collection<InventoryIssueItem> giftItems,
            User actor) {
        List<InventoryIssueItem> gifts = giftItems == null
                ? List.of()
                : giftItems.stream().filter(this::isGift).toList();
        if (gifts.isEmpty()) {
            return Set.of();
        }
        requirePersistedIssue(issue);
        if (transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                GIFT_REFERENCE, issue.getId(), InvTxnType.ORDER_DEDUCT)) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Quà tặng của phiếu xuất đã được giữ trước đó");
        }

        Set<UUID> changedVariantIds = new LinkedHashSet<>();
        for (GiftGroup group : sharedStockSupport.giftGroups(gifts).values()) {
            List<InventoryItem> inventories = sharedStockSupport.lockGroup(issue, group.variants());
            int onHand = inventories.stream().mapToInt(this::onHand).max().orElse(0);
            int reserved = inventories.stream().mapToInt(this::reserved).sum();
            int available = onHand - reserved;
            if (group.quantity() <= 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số lượng quà tặng phải lớn hơn 0");
            }
            if (available < group.quantity()) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                        "Quà tặng SKU " + group.displaySku() + " chỉ còn "
                                + Math.max(available, 0) + " sản phẩm có thể xuất");
            }

            InventoryItem holder = inventories.stream()
                    .filter(item -> group.holderVariantId().equals(item.getVariant().getId()))
                    .findFirst()
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));
            holder.setReservedQuantity(reserved(holder) + group.quantity());
            holder.setUpdatedBy(actor);
            inventoryItemRepository.save(holder);
            inventoryAlertService.notifyLowStockAfterStockChange(holder);

            transactionRepository.save(InventoryTransaction.builder()
                    .warehouse(issue.getWarehouse())
                    .variant(holder.getVariant())
                    .type(InvTxnType.ORDER_DEDUCT)
                    .referenceType(GIFT_REFERENCE)
                    .referenceId(issue.getId())
                    .quantityChange(-group.quantity())
                    .quantityBefore(available)
                    .quantityAfter(available - group.quantity())
                    .unitCost(group.unitCost())
                    .performedBy(actor)
                    .note("Reserved gift for stock delivery " + issue.getIssueCode())
                    .build());
            group.variants().forEach(variant -> changedVariantIds.add(variant.getId()));
        }
        return changedVariantIds;
    }

    /** Giải phóng reservation quà đúng một lần và không tác động reservation của đơn. */
    @Override
    @Transactional
    public Set<UUID> releaseGiftReservations(InventoryIssue issue, User actor) {
        requirePersistedIssue(issue);
        List<InventoryTransaction> reservations = giftReservations(issue.getId());
        if (reservations.isEmpty() || transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                GIFT_REFERENCE, issue.getId(), InvTxnType.ORDER_CANCEL)) {
            return Set.of();
        }

        Set<UUID> changedVariantIds = new LinkedHashSet<>();
        for (TransactionGroup group : sharedStockSupport.transactionGroups(reservations).values()) {
            List<InventoryItem> inventories = sharedStockSupport.lockGroup(issue, group.variants());
            int onHand = inventories.stream().mapToInt(this::onHand).max().orElse(0);
            int reservedBefore = inventories.stream().mapToInt(this::reserved).sum();

            for (InventoryTransaction reservation : group.transactions()) {
                InventoryItem holder = inventories.stream()
                        .filter(item -> reservation.getVariant().getId().equals(item.getVariant().getId()))
                        .findFirst()
                        .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));
                int quantity = Math.abs(safeQuantity(reservation.getQuantityChange()));
                if (reserved(holder) < quantity) {
                    throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                            "Reservation quà tặng không còn đủ cho SKU " + holder.getVariant().getSku());
                }
                holder.setReservedQuantity(reserved(holder) - quantity);
                holder.setUpdatedBy(actor);
                inventoryItemRepository.save(holder);
                inventoryAlertService.notifyLowStockAfterStockChange(holder);
            }

            int releasedQuantity = group.quantity();
            InventoryTransaction representative = group.transactions().get(0);
            transactionRepository.save(InventoryTransaction.builder()
                    .warehouse(issue.getWarehouse())
                    .variant(representative.getVariant())
                    .type(InvTxnType.ORDER_CANCEL)
                    .referenceType(GIFT_REFERENCE)
                    .referenceId(issue.getId())
                    .quantityChange(releasedQuantity)
                    .quantityBefore(onHand - reservedBefore)
                    .quantityAfter(onHand - reservedBefore + releasedQuantity)
                    .unitCost(representative.getUnitCost())
                    .performedBy(actor)
                    .note("Released gift reservation for stock delivery " + issue.getIssueCode())
                    .build());
            group.variants().forEach(variant -> changedVariantIds.add(variant.getId()));
        }
        return changedVariantIds;
    }

    /** Chuyển reservation quà đã xác thực thành giao dịch xuất kho OUTBOUND. */
    @Override
    @Transactional
    public void commitGiftReservations(InventoryIssue issue, User actor) {
        List<InventoryIssueItem> gifts = issue.getItems().stream().filter(this::isGift).toList();
        if (gifts.isEmpty()) {
            return;
        }
        requirePersistedIssue(issue);
        if (transactionRepository.existsByReferenceTypeAndReferenceIdAndType(
                GIFT_REFERENCE, issue.getId(), InvTxnType.ORDER_CANCEL)) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Reservation quà tặng đã được giải phóng");
        }

        Map<String, GiftGroup> expectedGroups = sharedStockSupport.giftGroups(gifts);
        Map<String, TransactionGroup> reservationGroups = sharedStockSupport
                .transactionGroups(giftReservations(issue.getId()));
        Map<String, Integer> expectedQuantities = expectedGroups.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().quantity()));
        Map<String, Integer> reservedQuantities = reservationGroups.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().quantity()));
        if (!expectedQuantities.equals(reservedQuantities)) {
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                    "Reservation quà tặng không khớp với phiếu xuất");
        }

        List<InventoryTransaction> existingOutbounds = transactionRepository
                .findByReferenceTypeAndReferenceIdAndType(
                        ISSUE_REFERENCE, issue.getId(), InvTxnType.OUTBOUND);
        for (Map.Entry<String, GiftGroup> entry : expectedGroups.entrySet()) {
            GiftGroup giftGroup = entry.getValue();
            TransactionGroup reservationGroup = reservationGroups.get(entry.getKey());
            List<InventoryItem> inventories = sharedStockSupport.lockGroup(issue, giftGroup.variants());

            Set<UUID> groupVariantIds = giftGroup.variants().stream()
                    .map(ProductVariant::getId)
                    .collect(Collectors.toSet());
            List<InventoryTransaction> orderOutbounds = existingOutbounds.stream()
                    .filter(transaction -> groupVariantIds.contains(transaction.getVariant().getId()))
                    .filter(transaction -> transaction.getNote() == null
                            || !transaction.getNote().startsWith(GIFT_OUTBOUND_NOTE))
                    .toList();
            // Order stock is consumed first; rebuild one physical on-hand value before applying the gift.
            int orderQuantity = orderOutbounds.stream()
                    .mapToInt(transaction -> Math.abs(safeQuantity(transaction.getQuantityChange())))
                    .sum();
            int physicalBefore = Math.max(
                    inventories.stream().mapToInt(this::onHand).max().orElse(0),
                    orderOutbounds.stream().mapToInt(transaction -> safeQuantity(transaction.getQuantityBefore()))
                            .max().orElse(0));
            int quantityBeforeGift = physicalBefore - orderQuantity;
            int quantityAfter = quantityBeforeGift - giftGroup.quantity();
            if (quantityAfter < 0) {
                throw new AppException(ErrorCode.NEGATIVE_STOCK_NOT_ALLOWED,
                        "Tồn kho không đủ để xuất quà tặng SKU " + giftGroup.displaySku());
            }

            Map<UUID, Integer> releasesByVariant = reservationGroup.transactions().stream()
                    .collect(Collectors.toMap(
                            transaction -> transaction.getVariant().getId(),
                            transaction -> Math.abs(safeQuantity(transaction.getQuantityChange())),
                            Integer::sum));
            for (InventoryItem inventory : inventories) {
                int release = releasesByVariant.getOrDefault(inventory.getVariant().getId(), 0);
                if (reserved(inventory) < release) {
                    throw new AppException(ErrorCode.INSUFFICIENT_STOCK,
                            "Reservation quà tặng không còn đủ cho SKU " + inventory.getVariant().getSku());
                }
                inventory.setQuantityOnHand(quantityAfter);
                if (release > 0) {
                    inventory.setReservedQuantity(reserved(inventory) - release);
                }
                inventory.setUpdatedBy(actor);
                inventoryItemRepository.save(inventory);
                inventoryAlertService.notifyLowStockAfterStockChange(inventory);
            }

            InventoryTransaction representative = reservationGroup.transactions().get(0);
            transactionRepository.save(InventoryTransaction.builder()
                    .warehouse(issue.getWarehouse())
                    .variant(representative.getVariant())
                    .type(InvTxnType.OUTBOUND)
                    .referenceType(ISSUE_REFERENCE)
                    .referenceId(issue.getId())
                    .quantityChange(-giftGroup.quantity())
                    .quantityBefore(quantityBeforeGift)
                    .quantityAfter(quantityAfter)
                    .unitCost(giftGroup.unitCost())
                    .performedBy(actor)
                    .note(GIFT_OUTBOUND_NOTE + ": " + issue.getIssueCode())
                    .build());
        }
    }

    private List<InventoryTransaction> giftReservations(UUID issueId) {
        return transactionRepository.findByReferenceTypeAndReferenceIdAndType(
                GIFT_REFERENCE, issueId, InvTxnType.ORDER_DEDUCT);
    }

    private void requirePersistedIssue(InventoryIssue issue) {
        if (issue == null || issue.getId() == null || issue.getWarehouse() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED,
                    "Phiếu xuất phải được lưu trước khi xử lý quà tặng");
        }
    }

    private boolean isGift(InventoryIssueItem item) {
        return item != null && Boolean.TRUE.equals(item.getIsGift());
    }

    private int onHand(InventoryItem item) {
        return safeQuantity(item.getQuantityOnHand());
    }

    private int reserved(InventoryItem item) {
        return safeQuantity(item.getReservedQuantity());
    }

    private int safeQuantity(Integer value) {
        return value == null ? 0 : value;
    }

}
