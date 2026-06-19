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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Override
    @Transactional
    public StockDeliveryResponse createStockDelivery(StockDeliveryRequest request) {
        log.info("Creating stock delivery for warehouse: {}", request.getWarehouseId());

        // Validate warehouse
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));

        if (!warehouse.getIsActive()) {
            throw new IllegalStateException("Warehouse is not active");
        }

        // Get current user
        User currentUser = getCurrentUser();
        String issueType = normalizeIssueType(request.getDeliveryType());
        String notes = request.getNote() != null ? request.getNote() : request.getNotes();
        OffsetDateTime issuedAt = request.getIssuedDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toOffsetDateTime();

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
                .createdAt(issuedAt)
                .totalCost(BigDecimal.ZERO)
                .build();
        inventoryIssue = inventoryIssueRepository.save(inventoryIssue);

        // Process delivery items
        for (StockDeliveryItemRequest itemRequest : request.getItems()) {
            ProductVariant productVariant = productVariantRepository.findById(itemRequest.getProductVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));

            // Check inventory availability
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByWarehouseIdAndVariantIdWithLock(warehouse.getId(), productVariant.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

            int quantityOnHand = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
            int reservedQuantity = inventoryItem.getReservedQuantity() != null ? inventoryItem.getReservedQuantity() : 0;
            int availableQuantity = inventoryItem.getAvailableQuantity() != null
                    ? inventoryItem.getAvailableQuantity()
                    : quantityOnHand - reservedQuantity;

            if (availableQuantity < itemRequest.getQuantity()) {
                throw new AppException(
                        ErrorCode.INSUFFICIENT_STOCK,
                        String.format("Insufficient inventory for %s. Available: %d, requested: %d",
                                productVariant.getName(),
                                availableQuantity,
                                itemRequest.getQuantity()));
            }

            // Get unit cost from inventory
            BigDecimal unitCost = inventoryItem.getAverageCost() != null
                    ? inventoryItem.getAverageCost()
                    : BigDecimal.ZERO;

            // Create delivery item
            InventoryIssueItem issueItem = InventoryIssueItem.builder()
                    .productVariant(productVariant)
                    .quantity(itemRequest.getQuantity())
                    .unitCost(unitCost)
                    .notes(itemRequest.getNote())
                    .build();

            inventoryIssue.addItem(issueItem);

            // Update inventory - decrease available quantity
            inventoryItem.setQuantityOnHand(quantityOnHand - itemRequest.getQuantity());
            inventoryItem.setUpdatedBy(currentUser);
            inventoryItemRepository.save(inventoryItem);

            // Create inventory transaction
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .warehouse(warehouse)
                    .variant(productVariant)
                    .type(InvTxnType.OUTBOUND)
                    .quantityChange(-itemRequest.getQuantity())
                    .quantityBefore(quantityOnHand)
                    .quantityAfter(quantityOnHand - itemRequest.getQuantity())
                    .unitCost(unitCost)
                    .referenceId(inventoryIssue.getId())
                    .referenceType("ISSUE")
                    .performedBy(currentUser)
                    .note("Stock delivery: " + issueType)
                    .build();

            inventoryTransactionRepository.save(transaction);
        }

        // Calculate totals
        inventoryIssue.calculateTotals();

        // Save inventory issue
        InventoryIssue savedIssue = inventoryIssueRepository.save(inventoryIssue);

        log.info("Stock delivery created successfully with ID: {}", savedIssue.getId());
        return stockDeliveryMapper.toResponse(savedIssue);
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
                : startDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
        OffsetDateTime endDateTime = endDate == null
                ? null
                : endDate.atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);

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

        InventoryIssue inventoryIssue = inventoryIssueRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ISSUE_NOT_FOUND));

        if (!"DRAFT".equals(inventoryIssue.getStatus())) {
            throw new IllegalStateException("Only DRAFT deliveries can be confirmed");
        }

        User currentUser = getCurrentUser();
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

        // Restore inventory quantities
        for (InventoryIssueItem item : inventoryIssue.getItems()) {
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByWarehouseIdAndVariantIdWithLock(
                            inventoryIssue.getWarehouse().getId(),
                            item.getProductVariant().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

            // Restore quantities
            int quantityBefore = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
            inventoryItem.setQuantityOnHand(quantityBefore + item.getQuantity());
            inventoryItem.setUpdatedBy(currentUser);
            inventoryItemRepository.save(inventoryItem);

            // Create reversal transaction
            InventoryTransaction reversalTransaction = InventoryTransaction.builder()
                    .warehouse(inventoryIssue.getWarehouse())
                    .variant(item.getProductVariant())
                    .type(InvTxnType.INBOUND)
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
        long count = inventoryIssueRepository.countByCreatedYear(currentYear);
        return "PX-" + currentYear + "-" + String.format("%03d", count + 1);
    }
}
