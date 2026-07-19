package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.StockDeliveryItemRequest;
import fu.osms.inventory.dto.request.StockDeliveryRequest;
import fu.osms.inventory.dto.response.StockDeliveryResponse;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StockDeliveryMapper;
import fu.osms.inventory.repository.*;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.service.StockDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockDeliveryServiceImpl implements StockDeliveryService {

    private final InventoryIssueRepository inventoryIssueRepository;
    private final InventoryIssueItemRepository inventoryIssueItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final UserRepository userRepository;
    private final StockDeliveryMapper stockDeliveryMapper;
    private final InventoryAlertService inventoryAlertService;

    @Override
    @Transactional
    public StockDeliveryResponse createStockDelivery(StockDeliveryRequest request) {
        log.info("Creating stock delivery for warehouse: {}", request.getWarehouseId());

        Warehouse warehouse = getActiveWarehouse(request.getWarehouseId());
        User currentUser = getCurrentUser();
        String issueType = normalizeIssueType(request.getDeliveryType());
        String notes = request.getNote() != null ? request.getNote() : request.getNotes();

        // Generate issue code
        String issueCode = generateIssueCode();

        // Create inventory issue (stock delivery)
        InventoryIssue inventoryIssue = InventoryIssue.builder()
                .warehouse(warehouse)
                .issueCode(issueCode)
                .issueType(issueType)
                .referenceId(request.getOrderId())
                .recipient(request.getRecipient())
                .notes(notes)
                .status("DRAFT")
                .createdBy(currentUser)
                .totalCost(BigDecimal.ZERO)
                .build();
        inventoryIssue = inventoryIssueRepository.save(inventoryIssue);

        for (StockDeliveryItemRequest itemRequest : request.getItems()) {
            inventoryIssue.addItem(buildIssueItem(warehouse, itemRequest));
        }

        inventoryIssue.calculateTotals();
        InventoryIssue savedIssue = inventoryIssueRepository.save(inventoryIssue);
        for (InventoryIssueItem item : savedIssue.getItems()) {
            reserveDraftInventory(savedIssue, item, currentUser);
        }
        log.info("Stock delivery created successfully with ID: {}", savedIssue.getId());
        return stockDeliveryMapper.toResponse(savedIssue);
    }

    @Override
    @Transactional
    public StockDeliveryResponse updateStockDelivery(UUID id, StockDeliveryRequest request) {
        log.info("Updating stock delivery: {}", id);

        InventoryIssue inventoryIssue = inventoryIssueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.ISSUE_NOT_FOUND));

        if (!"DRAFT".equals(inventoryIssue.getStatus())) {
            throw new AppException(ErrorCode.ISSUE_ALREADY_CONFIRMED, "Confirmed delivery documents are read-only.");
        }

        Warehouse warehouse = getActiveWarehouse(request.getWarehouseId());
        User currentUser = getCurrentUser();
        releaseDraftReservations(inventoryIssue, currentUser, "Stock delivery draft updated - reservation released");
        String notes = request.getNote() != null ? request.getNote() : request.getNotes();
        String issueType = normalizeIssueType(request.getDeliveryType());

        inventoryIssue.setWarehouse(warehouse);
        inventoryIssue.setIssueType(issueType);
        inventoryIssue.setRecipient(request.getRecipient());
        inventoryIssue.setNotes(notes);
        inventoryIssue.getItems().clear();

        for (StockDeliveryItemRequest itemRequest : request.getItems()) {
            inventoryIssue.addItem(buildIssueItem(warehouse, itemRequest));
        }

        inventoryIssue.calculateTotals();
        InventoryIssue updatedIssue = inventoryIssueRepository.save(inventoryIssue);
        for (InventoryIssueItem item : updatedIssue.getItems()) {
            reserveDraftInventory(updatedIssue, item, currentUser);
        }
        log.info("Stock delivery updated successfully: {}", id);
        return stockDeliveryMapper.toResponse(updatedIssue);
    }

    @Override
    @Transactional(readOnly = true)
    public StockDeliveryResponse getStockDeliveryById(UUID id) {
        log.info("Fetching stock delivery by ID: {}", id);

        InventoryIssue inventoryIssue = inventoryIssueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.ISSUE_NOT_FOUND));

        return stockDeliveryMapper.toResponse(inventoryIssue);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockDeliveryResponse> getAllStockDeliveries(
            UUID warehouseId,
            String status,
            String deliveryType,
            String keyword,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {

        log.info("Fetching all stock deliveries with filters");

        OffsetDateTime startDateTime = startDate == null
                ? null
                : startDate.atStartOfDay(ZoneId.systemDefault()).toOffsetDateTime();
        OffsetDateTime endDateTime = endDate == null
                ? null
                : endDate.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toOffsetDateTime();

        Specification<InventoryIssue> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (warehouseId != null) {
                predicates.add(criteriaBuilder.equal(root.get("warehouse").get("id"), warehouseId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (deliveryType != null && !deliveryType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("issueType"), normalizeIssueType(deliveryType)));
            }
            if (startDateTime != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDateTime));
            }
            if (endDateTime != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDateTime));
            }
            if (keyword != null && !keyword.isBlank()) {
                String likeKeyword = "%" + keyword.trim().toLowerCase() + "%";
                log.info("Applying keyword search with keyword: {}", likeKeyword);
                var warehouseJoin = root.join("warehouse", JoinType.LEFT);
                var createdByJoin = root.join("createdBy", JoinType.LEFT);
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("issueCode")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("notes")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(warehouseJoin.get("name")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(createdByJoin.get("fullName")), likeKeyword),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("recipient")), likeKeyword)
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<InventoryIssue> issuesPage = inventoryIssueRepository.findAll(spec, pageable);

        return issuesPage.map(stockDeliveryMapper::toResponse);
    }

    @Override
    @Transactional
    public StockDeliveryResponse confirmStockDelivery(UUID id) {
        log.info("Confirming stock delivery: {}", id);

        InventoryIssue inventoryIssue = inventoryIssueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.ISSUE_NOT_FOUND));

        if (!"DRAFT".equals(inventoryIssue.getStatus())) {
            throw new AppException(ErrorCode.ISSUE_ALREADY_CONFIRMED, "Order has already been processed for delivery.");
        }

        User currentUser = getCurrentUser();
        for (InventoryIssueItem item : inventoryIssue.getItems()) {
            commitDraftReservation(inventoryIssue, item, currentUser);
        }
        inventoryIssue.setStatus("CONFIRMED");
        inventoryIssue.setApprovedBy(currentUser);
        inventoryIssue.setConfirmedAt(java.time.OffsetDateTime.now());

        InventoryIssue updatedIssue = inventoryIssueRepository.save(inventoryIssue);
        log.info("Stock delivery confirmed successfully: {}", id);
        return stockDeliveryMapper.toResponse(updatedIssue);
    }

    @Override
    @Transactional
    public StockDeliveryResponse cancelStockDelivery(UUID id) {
        log.info("Cancelling stock delivery: {}", id);

        InventoryIssue inventoryIssue = inventoryIssueRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.ISSUE_NOT_FOUND));

        if ("CANCELLED".equals(inventoryIssue.getStatus())) {
            throw new IllegalStateException("Stock delivery is already cancelled");
        }

        User currentUser = getCurrentUser();

        if ("DRAFT".equals(inventoryIssue.getStatus())) {
            releaseDraftReservations(inventoryIssue, currentUser, "Stock delivery draft cancelled - reservation released");
        } else if ("CONFIRMED".equals(inventoryIssue.getStatus())) {
            for (InventoryIssueItem item : inventoryIssue.getItems()) {
                InventoryItem inventoryItem = inventoryItemRepository
                        .findByWarehouseIdAndVariantIdWithLock(
                                inventoryIssue.getWarehouse().getId(),
                                item.getProductVariant().getId())
                        .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

                int quantityBefore = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
                inventoryItem.setQuantityOnHand(quantityBefore + item.getQuantity());
                inventoryItem.setUpdatedBy(currentUser);
                inventoryItemRepository.save(inventoryItem);

                InventoryTransaction reversalTransaction = InventoryTransaction.builder()
                        .warehouse(inventoryIssue.getWarehouse())
                        .variant(item.getProductVariant())
                        .type(InvTxnType.ORDER_CANCEL)
                        .quantityChange(item.getQuantity())
                        .quantityBefore(quantityBefore)
                        .quantityAfter(quantityBefore + item.getQuantity())
                        .unitCost(item.getUnitCost())
                        .referenceId(inventoryIssue.getId())
                        .referenceType("ISSUE")
                        .performedBy(currentUser)
                        .note("Stock delivery cancelled - inventory restored")
                        .build();

                inventoryTransactionRepository.save(reversalTransaction);
            }
        }

        inventoryIssue.setStatus("CANCELLED");
        inventoryIssue.setApprovedBy(currentUser);
        InventoryIssue updatedIssue = inventoryIssueRepository.save(inventoryIssue);
        log.info("Stock delivery cancelled successfully: {}", id);
        return stockDeliveryMapper.toResponse(updatedIssue);
    }

    @Override
    @Transactional(readOnly = true)
    public Object getDeliveryStatistics() {
        log.info("Fetching delivery statistics");

        Map<String, Object> statistics = new HashMap<>();
        statistics.put("draftCount", inventoryIssueRepository.countByStatus("DRAFT"));
        statistics.put("confirmedCount", inventoryIssueRepository.countByStatus("CONFIRMED"));
        statistics.put("cancelledCount", inventoryIssueRepository.countByStatus("CANCELLED"));
        statistics.put("orderCount", inventoryIssueRepository.countByIssueType("ORDER"));
        statistics.put("adjustmentCount", inventoryIssueRepository.countByIssueType("ADJUSTMENT"));
        statistics.put("disposalCount", inventoryIssueRepository.countByIssueType("DISPOSAL"));
        statistics.put("transferCount", inventoryIssueRepository.countByIssueType("TRANSFER"));
        statistics.put("totalCount", inventoryIssueRepository.countDeliveries());
        statistics.put("totalValue", inventoryIssueRepository.sumTotalCost());

        return statistics;
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    private Warehouse getActiveWarehouse(UUID warehouseId) {
        if (warehouseId == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Please select a delivery warehouse.");
        }

        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));

        if (!warehouse.getIsActive()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Please select a delivery warehouse.");
        }
        return warehouse;
    }

    private InventoryIssueItem buildIssueItem(Warehouse warehouse, StockDeliveryItemRequest itemRequest) {
        ProductVariant productVariant = productVariantRepository.findById(itemRequest.getProductVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));

        InventoryItem inventoryItem = inventoryItemRepository
                .findByWarehouseIdAndVariantIdWithLock(warehouse.getId(), productVariant.getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

        validateAvailableQuantity(inventoryItem, itemRequest.getQuantity());

        BigDecimal unitCost = inventoryItem.getAverageCost() != null
                ? inventoryItem.getAverageCost()
                : BigDecimal.ZERO;

        return InventoryIssueItem.builder()
                .productVariant(productVariant)
                .quantity(itemRequest.getQuantity())
                .unitCost(unitCost)
                .notes(itemRequest.getNote())
                .build();
    }

    private void reserveDraftInventory(InventoryIssue inventoryIssue, InventoryIssueItem item, User currentUser) {
        InventoryItem inventoryItem = inventoryItemRepository
                .findByWarehouseIdAndVariantIdWithLock(
                        inventoryIssue.getWarehouse().getId(),
                        item.getProductVariant().getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

        validateAvailableQuantity(inventoryItem, item.getQuantity());

        int quantityOnHand = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
        int reservedBefore = inventoryItem.getReservedQuantity() != null ? inventoryItem.getReservedQuantity() : 0;
        int availableBefore = quantityOnHand - reservedBefore;
        int reservedAfter = reservedBefore + item.getQuantity();
        int availableAfter = quantityOnHand - reservedAfter;

        inventoryItem.setReservedQuantity(reservedAfter);
        inventoryItem.setUpdatedBy(currentUser);
        inventoryItemRepository.save(inventoryItem);
        inventoryAlertService.notifyLowStockAfterStockChange(inventoryItem);

        inventoryTransactionRepository.save(InventoryTransaction.builder()
                .warehouse(inventoryIssue.getWarehouse())
                .variant(item.getProductVariant())
                .type(InvTxnType.ORDER_DEDUCT)
                .quantityChange(-item.getQuantity())
                .quantityBefore(availableBefore)
                .quantityAfter(availableAfter)
                .unitCost(item.getUnitCost())
                .referenceId(inventoryIssue.getId())
                .referenceType("ISSUE")
                .performedBy(currentUser)
                .note("Stock delivery draft reserved: " + inventoryIssue.getIssueType())
                .build());
    }

    private void releaseDraftReservations(InventoryIssue inventoryIssue, User currentUser, String note) {
        for (InventoryIssueItem item : inventoryIssue.getItems()) {
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByWarehouseIdAndVariantIdWithLock(
                            inventoryIssue.getWarehouse().getId(),
                            item.getProductVariant().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

            int quantityOnHand = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
            int reservedBefore = inventoryItem.getReservedQuantity() != null ? inventoryItem.getReservedQuantity() : 0;
            int availableBefore = quantityOnHand - reservedBefore;
            int requestedReleaseQuantity = item.getQuantity() != null ? item.getQuantity() : 0;
            int releaseQuantity = Math.min(requestedReleaseQuantity, reservedBefore);
            if (releaseQuantity <= 0) {
                log.warn(
                        "Skipping reservation release for issue {} variant {} because reserved quantity is {}",
                        inventoryIssue.getId(),
                        item.getProductVariant().getId(),
                        reservedBefore);
                continue;
            }
            int reservedAfter = reservedBefore - releaseQuantity;
            int availableAfter = quantityOnHand - reservedAfter;

            inventoryItem.setReservedQuantity(reservedAfter);
            inventoryItem.setUpdatedBy(currentUser);
            inventoryItemRepository.save(inventoryItem);
            inventoryAlertService.notifyLowStockAfterStockChange(inventoryItem);

            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .warehouse(inventoryIssue.getWarehouse())
                    .variant(item.getProductVariant())
                    .type(InvTxnType.ORDER_CANCEL)
                    .quantityChange(releaseQuantity)
                    .quantityBefore(availableBefore)
                    .quantityAfter(availableAfter)
                    .unitCost(item.getUnitCost())
                    .referenceId(inventoryIssue.getId())
                    .referenceType("ISSUE")
                    .performedBy(currentUser)
                    .note(note)
                    .build());
        }
    }

    private void commitDraftReservation(InventoryIssue inventoryIssue, InventoryIssueItem item, User currentUser) {
        InventoryItem inventoryItem = inventoryItemRepository
                .findByWarehouseIdAndVariantIdWithLock(
                        inventoryIssue.getWarehouse().getId(),
                        item.getProductVariant().getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

        int quantity = item.getQuantity() != null ? item.getQuantity() : 0;
        int quantityBefore = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
        int reservedBefore = inventoryItem.getReservedQuantity() != null ? inventoryItem.getReservedQuantity() : 0;
        int quantityAfter = quantityBefore - quantity;
        int reservedAfter = reservedBefore - quantity;

        if (quantityAfter < 0) {
            throw new AppException(
                    ErrorCode.NEGATIVE_STOCK_NOT_ALLOWED,
                    "Delivery quantity exceeds available inventory. Please review the document before saving.");
        }
        if (reservedAfter < 0) {
            throw new AppException(
                    ErrorCode.INSUFFICIENT_STOCK,
                    "Reserved inventory is not enough to confirm this delivery.");
        }

        inventoryItem.setQuantityOnHand(quantityAfter);
        inventoryItem.setReservedQuantity(reservedAfter);
        inventoryItem.setUpdatedBy(currentUser);
        inventoryItem = inventoryItemRepository.save(inventoryItem);
        inventoryAlertService.notifyLowStockAfterStockChange(inventoryItem);

        inventoryTransactionRepository.save(InventoryTransaction.builder()
                .warehouse(inventoryIssue.getWarehouse())
                .variant(item.getProductVariant())
                .type(InvTxnType.OUTBOUND)
                .quantityChange(-quantity)
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .unitCost(item.getUnitCost())
                .referenceId(inventoryIssue.getId())
                .referenceType("ISSUE")
                .performedBy(currentUser)
                .note("Stock delivery confirmed: " + inventoryIssue.getIssueType())
                .build());
    }

    private void validateAvailableQuantity(InventoryItem inventoryItem, Integer requestedQuantity) {
        int quantity = requestedQuantity == null ? 0 : requestedQuantity;
        if (quantity <= 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Delivery quantity must be greater than 0.");
        }

        int quantityOnHand = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
        int reservedQuantity = inventoryItem.getReservedQuantity() != null ? inventoryItem.getReservedQuantity() : 0;
        int availableQuantity = quantityOnHand - reservedQuantity;

        if (availableQuantity < quantity) {
            throw new AppException(
                    ErrorCode.INSUFFICIENT_STOCK,
                    "Insufficient inventory to complete delivery.");
        }

        if (quantityOnHand - quantity < 0) {
            throw new AppException(
                    ErrorCode.NEGATIVE_STOCK_NOT_ALLOWED,
                    "Delivery quantity exceeds available inventory. Please review the document before saving.");
        }
    }

    private String normalizeIssueType(String issueType) {
        if (issueType == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Issue type is required");
        }
        return switch (issueType) {
            case "ORDER", "ADJUSTMENT", "DISPOSAL", "TRANSFER" -> issueType;
            default -> throw new AppException(
                    ErrorCode.VALIDATION_FAILED,
                    "Issue type must be ORDER, ADJUSTMENT, DISPOSAL, or TRANSFER"
            );
        };
    }

    private String generateIssueCode() {
        int currentYear = LocalDate.now().getYear();
        String prefix = "PX-" + currentYear + "-";

        // Use max-of-code (instead of count + 1) so two concurrent calls do
        // not generate the same suffix. Filter by prefix so other code
        // schemes do not interfere.
        Optional<InventoryIssue> latest =
                inventoryIssueRepository.findTopByIssueCodeStartingWithOrderByIssueCodeDesc(prefix);
        if (latest.isEmpty()) {
            return prefix + "001";
        }
        String latestCode = latest.get().getIssueCode();
        try {
            int number = Integer.parseInt(latestCode.substring(prefix.length()));
            return prefix + String.format("%03d", number + 1);
        } catch (Exception e) {
            return prefix + "001";
        }
    }
}
