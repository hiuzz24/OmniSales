package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.StockReceiveItemRequest;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveItemResponse;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StockReceiveMapper;
import fu.osms.inventory.repository.*;
import fu.osms.inventory.service.StockReceiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReceiveServiceImpl implements StockReceiveService {

    private final StockReceiveRepository stockReceiveRepository;
    private final StockReceiveItemRepository stockReceiveItemRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final SupplierRepository supplierRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final StockReceiveMapper receiptMapper;

    @Override
    @Transactional
    public StockReceiveResponse createReceipt(StockReceiveRequest request, UUID createdByUserId) {
        // 1. Validate warehouse
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
        if (Boolean.FALSE.equals(warehouse.getIsActive())) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        // 2. Validate supplier (optional)
        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
        }

        // 3. Validate items not empty (double-check)
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Receipt must have at least one item");
        }

        // 4. Validate based on status (DRAFT vs CONFIRMED)
        boolean isDraft = Boolean.TRUE.equals(request.getIsDraft());
        if (!isDraft) {
            // When confirming (not draft), validate all items have quantity and unitCost
            for (StockReceiveItemRequest item : request.getItems()) {
                // Số lượng bắt buộc phải lớn hơn 0
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED, 
                        "Tất cả sản phẩm phải có số lượng lớn hơn 0 khi xác nhận phiếu nhập");
                }
                // Đơn giá phải lớn hơn hoặc bằng 0
                if (item.getUnitCost() == null || item.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Tất cả sản phẩm phải có đơn giá lớn hơn hoặc bằng 0 khi xác nhận phiếu nhập");
                }
            }
        } else {
            // For DRAFT: set default values for null quantity/unitCost and validate if provided
            for (StockReceiveItemRequest item : request.getItems()) {
                if (item.getQuantity() == null) {
                    item.setQuantity(0);
                } else if (item.getQuantity() <= 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Số lượng phải lớn hơn 0");
                }

                if (item.getUnitCost() == null) {
                    item.setUnitCost(BigDecimal.ZERO);
                } else if (item.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Đơn giá phải lớn hơn hoặc bằng 0");
                }
            }
        }

        // 5. Validate no duplicate variantId
        Set<UUID> variantIdSet = new HashSet<>();
        for (StockReceiveItemRequest item : request.getItems()) {
            if (!variantIdSet.add(item.getVariantId())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Duplicate variant in receipt items");
            }
        }

        // 6. Load and validate each ProductVariant
        List<ProductVariant> variants = new ArrayList<>();
        for (StockReceiveItemRequest item : request.getItems()) {
            ProductVariant variant = variantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
            if (Boolean.FALSE.equals(variant.getIsActive()) || variant.getDeletedAt() != null) {
                throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
            }
            variants.add(variant);
        }

        // 7. Calculate totalCost BEFORE creating receipt
        List<StockReceiveItemRequest> itemRequests = request.getItems();
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockReceiveItemRequest itemReq : itemRequests) {
            BigDecimal itemTotal = BigDecimal.valueOf(itemReq.getQuantity()).multiply(itemReq.getUnitCost());
            totalCost = totalCost.add(itemTotal);
        }

        // 8. Generate receiptCode: "PN-{YYYY}-{SEQ}"
        String receiptCode = getNextReceiptCode();

        // 9. Load createdBy user (optional)
        var createdByUser = userRepository.findById(createdByUserId).orElse(null);

        // 10. Build and save InventoryReceipt with totalCost already calculated
        OffsetDateTime receivedAt = resolveDocumentTime(request.getReceivedAt());

        // Determine status based on isDraft flag
        String status = Boolean.TRUE.equals(request.getIsDraft()) ? "DRAFT" : "CONFIRMED";
        OffsetDateTime confirmedAt = Boolean.TRUE.equals(request.getIsDraft()) ? null : OffsetDateTime.now();

        InventoryReceipt receipt = InventoryReceipt.builder()
                .warehouse(warehouse)
                .supplier(supplier)
                .receiptCode(receiptCode)
                .invoiceNumber(receiptCode)
                .status(status)
                .confirmedAt(confirmedAt)
                .receivedAt(receivedAt)
                .notes(request.getNotes())
                .createdBy(createdByUser)
                .totalCost(totalCost)  // Set totalCost from the beginning
                .build();

        receipt = stockReceiveRepository.save(receipt);

        // 11. Process each item
        List<InventoryReceiptItem> savedItems = new ArrayList<>();

        for (int i = 0; i < itemRequests.size(); i++) {
            StockReceiveItemRequest itemReq = itemRequests.get(i);
            ProductVariant productVariant = variants.get(i);

            // a. Create InventoryReceiptItem (always save for both DRAFT and CONFIRMED)
            InventoryReceiptItem receiptItem = InventoryReceiptItem.builder()
                    .receipt(receipt)
                    .variant(productVariant)
                    .quantity(itemReq.getQuantity())
                    .unitCost(itemReq.getUnitCost())
                    .notes(itemReq.getNotes())
                    .build();

            // b. Only update inventory if status is CONFIRMED
            if ("CONFIRMED".equals(status)) {
                // Find or create InventoryItem
                InventoryItem inventoryItem = inventoryItemRepository
                        .findByWarehouseIdAndVariantId(warehouse.getId(), productVariant.getId())
                        .orElse(null);

                if (inventoryItem == null) {
                    // Create new InventoryItem first, then apply pessimistic lock path
                    inventoryItem = InventoryItem.builder()
                            .warehouse(warehouse)
                            .variant(productVariant)
                            .quantityOnHand(0)
                            .averageCost(BigDecimal.ZERO)
                            .build();
                    inventoryItem = inventoryItemRepository.save(inventoryItem);
                }

                // Re-fetch with pessimistic lock
                inventoryItem = inventoryItemRepository
                        .findByWarehouseIdAndVariantIdWithLock(warehouse.getId(), productVariant.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

                int quantity = itemReq.getQuantity();
                BigDecimal unitCost = itemReq.getUnitCost();

                CostUpdateResult costUpdate = applyReceiptCostAndQuantity(
                        inventoryItem, productVariant, quantity, unitCost, createdByUser);

                receiptItem.setAvgCostBefore(costUpdate.avgCostBefore());
                receiptItem.setAvgCostAfter(costUpdate.avgCostAfter());
            }

            // c. Save receiptItem (for both DRAFT and CONFIRMED)
            receiptItem = stockReceiveItemRepository.save(receiptItem);
            savedItems.add(receiptItem);

            // d. Create InventoryTransaction (for both DRAFT and CONFIRMED)
            // For DRAFT: just record the transaction without updating actual inventory
            // For CONFIRMED: record the actual inventory changes
            int transactionQtyBefore = 0;
            int transactionQtyAfter = 0;
            
            if ("CONFIRMED".equals(status)) {
                // For confirmed receipt, get the actual inventory quantities
                InventoryItem inventoryItem = inventoryItemRepository
                        .findByWarehouseIdAndVariantId(warehouse.getId(), productVariant.getId())
                        .orElse(null);
                if (inventoryItem != null) {
                    transactionQtyBefore = inventoryItem.getQuantityOnHand() - itemReq.getQuantity(); // Before the import
                    transactionQtyAfter = inventoryItem.getQuantityOnHand(); // After the import
                }
            } else {
                // For DRAFT, set transaction quantities to reflect the intended change
                // quantityBefore = 0, quantityAfter = quantity_change (to satisfy constraint)
                transactionQtyBefore = 0;
                transactionQtyAfter = itemReq.getQuantity();
            }
            
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .type(InvTxnType.IMPORT)
                    .referenceType("RECEIPT")
                    .referenceId(receipt.getId())
                    .quantityChange(itemReq.getQuantity())
                    .quantityBefore(transactionQtyBefore)
                    .quantityAfter(transactionQtyAfter)
                    .unitCost(itemReq.getUnitCost())
                    .warehouse(warehouse)
                    .variant(productVariant)
                    .performedBy(createdByUser)
                    .performedAt(OffsetDateTime.now())
                    .note("DRAFT".equals(status) ? "DRAFT - Not applied to inventory" : null)
                    .build();
            inventoryTransactionRepository.save(transaction);
        }

        // 12. Build and return response (no need to update totalCost again)
        StockReceiveResponse response = receiptMapper.toResponse(receipt);
        List<StockReceiveItemResponse> itemResponses = savedItems.stream()
                .map(receiptMapper::toItemResponse)
                .toList();
        response.setItems(itemResponses);
        
        // Calculate totalSkuCount and totalQuantity
        response.setTotalSkuCount(savedItems.size());
        response.setTotalQuantity(savedItems.stream()
                .mapToInt(InventoryReceiptItem::getQuantity)
                .sum());

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StockReceiveResponse> getReceipts(int page, int size) {
        Page<InventoryReceipt> receiptsPage = stockReceiveRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));

        List<StockReceiveResponse> content = receiptsPage.getContent().stream()
                .map(receipt -> {
                    // Map basic receipt information
                    StockReceiveResponse response = receiptMapper.toResponse(receipt);
                    
                    // Fetch and map items for this receipt
                    List<InventoryReceiptItem> items = stockReceiveItemRepository.findByReceiptId(receipt.getId());
                    List<StockReceiveItemResponse> itemResponses = items.stream()
                            .map(receiptMapper::toItemResponse)
                            .toList();
                    response.setItems(itemResponses);
                    
                    // Calculate totalSkuCount and totalQuantity
                    response.setTotalSkuCount(items.size());
                    response.setTotalQuantity(items.stream()
                            .mapToInt(InventoryReceiptItem::getQuantity)
                            .sum());
                    
                    return response;
                })
                .toList();

        return PageResponse.<StockReceiveResponse>builder()
                .content(content)
                .page(receiptsPage.getNumber())
                .size(receiptsPage.getSize())
                .totalElements(receiptsPage.getTotalElements())
                .totalPages(receiptsPage.getTotalPages())
                .first(receiptsPage.isFirst())
                .last(receiptsPage.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public StockReceiveResponse getReceiptById(UUID id) {
        InventoryReceipt receipt = stockReceiveRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));

        List<InventoryReceiptItem> items = stockReceiveItemRepository.findByReceiptId(id);

        StockReceiveResponse response = receiptMapper.toResponse(receipt);
        List<StockReceiveItemResponse> itemResponses = items.stream()
                .map(receiptMapper::toItemResponse)
                .toList();
        response.setItems(itemResponses);
        
        // Calculate totalSkuCount and totalQuantity
        response.setTotalSkuCount(items.size());
        response.setTotalQuantity(items.stream()
                .mapToInt(InventoryReceiptItem::getQuantity)
                .sum());

        return response;
    }

    @Override
    @Transactional
    public StockReceiveResponse updateReceipt(UUID receiptId, StockReceiveRequest request, UUID updatedByUserId) {
        // 1. Load existing receipt
        InventoryReceipt receipt = stockReceiveRepository.findById(receiptId)
                .orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));

        // 2. Validate receipt status is DRAFT
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, 
                "Chỉ có thể chỉnh sửa phiếu nhập ở trạng thái Lưu tạm");
        }

        // 3. Validate warehouse
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));
        if (Boolean.FALSE.equals(warehouse.getIsActive())) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        // 4. Validate supplier (optional)
        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new AppException(ErrorCode.SUPPLIER_NOT_FOUND));
        }

        // 5. Validate items not empty
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Receipt must have at least one item");
        }

        // 7. For DRAFT: set default values for null quantity/unitCost
        for (StockReceiveItemRequest item : request.getItems()) {
            if (item.getQuantity() == null) {
                item.setQuantity(0);
            }
            if (item.getUnitCost() == null) {
                item.setUnitCost(BigDecimal.ZERO);
            }
        }

        // 8. Validate no duplicate variantId
        Set<UUID> variantIdSet = new HashSet<>();
        for (StockReceiveItemRequest item : request.getItems()) {
            if (!variantIdSet.add(item.getVariantId())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, "Duplicate variant in receipt items");
            }
        }

        // 9. Load and validate each ProductVariant
        List<ProductVariant> variants = new ArrayList<>();
        for (StockReceiveItemRequest item : request.getItems()) {
            ProductVariant variant = variantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
            if (Boolean.FALSE.equals(variant.getIsActive()) || variant.getDeletedAt() != null) {
                throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
            }
            variants.add(variant);
        }

        // 10. Calculate totalCost
        BigDecimal totalCost = BigDecimal.ZERO;
        for (StockReceiveItemRequest itemReq : request.getItems()) {
            BigDecimal itemTotal = BigDecimal.valueOf(itemReq.getQuantity()).multiply(itemReq.getUnitCost());
            totalCost = totalCost.add(itemTotal);
        }

        // 11. Load updatedBy user
        var updatedByUser = userRepository.findById(updatedByUserId).orElse(null);

        // 12. Update receipt basic info
        OffsetDateTime receivedAt = resolveDocumentTime(request.getReceivedAt());

        receipt.setWarehouse(warehouse);
        receipt.setSupplier(supplier);
        receipt.setInvoiceNumber(receipt.getReceiptCode());
        receipt.setReceivedAt(receivedAt);
        receipt.setNotes(request.getNotes());
        receipt.setTotalCost(totalCost);
        receipt.setUpdatedAt(OffsetDateTime.now());
        receipt = stockReceiveRepository.save(receipt);

        // 13. Delete old items
        List<InventoryReceiptItem> oldItems = stockReceiveItemRepository.findByReceiptId(receiptId);
        stockReceiveItemRepository.deleteAll(oldItems);

        // 14. DO NOT delete old transactions - keep them for audit trail
        // Old transactions remain as history

        // 15. Create new items
        List<InventoryReceiptItem> savedItems = new ArrayList<>();
        for (int i = 0; i < request.getItems().size(); i++) {
            StockReceiveItemRequest itemReq = request.getItems().get(i);
            ProductVariant productVariant = variants.get(i);

            // Create InventoryReceiptItem
            InventoryReceiptItem receiptItem = InventoryReceiptItem.builder()
                    .receipt(receipt)
                    .variant(productVariant)
                    .quantity(itemReq.getQuantity())
                    .unitCost(itemReq.getUnitCost())
                    .notes(itemReq.getNotes())
                    .build();

            receiptItem = stockReceiveItemRepository.save(receiptItem);
            savedItems.add(receiptItem);

            // Create DRAFT transaction
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .type(InvTxnType.IMPORT)
                    .referenceType("RECEIPT")
                    .referenceId(receipt.getId())
                    .quantityChange(itemReq.getQuantity())
                    .quantityBefore(0)
                    .quantityAfter(itemReq.getQuantity())
                    .unitCost(itemReq.getUnitCost())
                    .warehouse(warehouse)
                    .variant(productVariant)
                    .performedBy(updatedByUser)
                    .performedAt(OffsetDateTime.now())
                    .note("DRAFT - Updated - Not applied to inventory")
                    .build();
            inventoryTransactionRepository.save(transaction);
        }

        // 16. Build and return response
        StockReceiveResponse response = receiptMapper.toResponse(receipt);
        List<StockReceiveItemResponse> itemResponses = savedItems.stream()
                .map(receiptMapper::toItemResponse)
                .toList();
        response.setItems(itemResponses);
        
        // Calculate totalSkuCount and totalQuantity
        response.setTotalSkuCount(savedItems.size());
        response.setTotalQuantity(savedItems.stream()
                .mapToInt(InventoryReceiptItem::getQuantity)
                .sum());

        return response;
    }

    @Override
    @Transactional
    public StockReceiveResponse completeReceipt(UUID receiptId, UUID approvedByUserId) {
        // 1. Load receipt with pessimistic lock
        InventoryReceipt receipt = stockReceiveRepository.findById(receiptId)
                .orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));

        // 2. Validate receipt status is DRAFT
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, 
                "Chỉ có thể hoàn thành phiếu nhập ở trạng thái Lưu tạm");
        }

        // 3. Load receipt items
        List<InventoryReceiptItem> receiptItems = stockReceiveItemRepository.findByReceiptId(receiptId);
        if (receiptItems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiếu nhập không có sản phẩm nào");
        }

        // 4. Validate all items have valid quantity and unitCost
        for (InventoryReceiptItem item : receiptItems) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, 
                    String.format("Sản phẩm '%s' (SKU: %s) phải có số lượng lớn hơn 0", 
                    item.getVariant().getProduct().getName(),
                    item.getVariant().getSku()));
            }
            if (item.getUnitCost() == null || item.getUnitCost().compareTo(BigDecimal.ZERO) <= 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, 
                    String.format("Sản phẩm '%s' (SKU: %s) phải có đơn giá lớn hơn 0", 
                    item.getVariant().getProduct().getName(),
                    item.getVariant().getSku()));
            }
        }

        // 5. Load approvedBy user
        var approvedByUser = userRepository.findById(approvedByUserId).orElse(null);

        // 6. Update inventory for each item
        for (InventoryReceiptItem receiptItem : receiptItems) {
            ProductVariant productVariant = receiptItem.getVariant();
            Warehouse warehouse = receipt.getWarehouse();

            // Find or create InventoryItem
            InventoryItem inventoryItem = inventoryItemRepository
                    .findByWarehouseIdAndVariantId(warehouse.getId(), productVariant.getId())
                    .orElse(null);

            if (inventoryItem == null) {
                inventoryItem = InventoryItem.builder()
                        .warehouse(warehouse)
                        .variant(productVariant)
                        .quantityOnHand(0)
                        .averageCost(BigDecimal.ZERO)
                        .build();
                inventoryItem = inventoryItemRepository.save(inventoryItem);
            }

            // Re-fetch with pessimistic lock
            inventoryItem = inventoryItemRepository
                    .findByWarehouseIdAndVariantIdWithLock(warehouse.getId(), productVariant.getId())
                    .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));

            int qtyBefore = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
            int quantity = receiptItem.getQuantity();
            BigDecimal unitCost = receiptItem.getUnitCost();
            CostUpdateResult costUpdate = applyReceiptCostAndQuantity(
                    inventoryItem, productVariant, quantity, unitCost, approvedByUser);

            // Update receiptItem with cost tracking
            receiptItem.setAvgCostBefore(costUpdate.avgCostBefore());
            receiptItem.setAvgCostAfter(costUpdate.avgCostAfter());
            stockReceiveItemRepository.save(receiptItem);

            // CREATE NEW transaction for CONFIRMED receipt (do NOT update old DRAFT transaction)
            // The DRAFT transaction remains as audit trail
            InventoryTransaction confirmedTransaction = InventoryTransaction.builder()
                    .type(InvTxnType.IMPORT)
                    .referenceType("RECEIPT")
                    .referenceId(receiptId)
                    .quantityChange(quantity)
                    .quantityBefore(qtyBefore)
                    .quantityAfter(qtyBefore + quantity)
                    .unitCost(unitCost)
                    .warehouse(warehouse)
                    .variant(productVariant)
                    .performedBy(approvedByUser)
                    .performedAt(OffsetDateTime.now())
                    .note("Completed from DRAFT")
                    .build();
            inventoryTransactionRepository.save(confirmedTransaction);
        }

        // 7. Update receipt status to CONFIRMED
        receipt.setStatus("CONFIRMED");
        receipt.setConfirmedAt(OffsetDateTime.now());
        receipt.setApprovedBy(approvedByUser);
        receipt = stockReceiveRepository.save(receipt);

        // 8. Build and return response
        StockReceiveResponse response = receiptMapper.toResponse(receipt);
        List<StockReceiveItemResponse> itemResponses = receiptItems.stream()
                .map(receiptMapper::toItemResponse)
                .toList();
        response.setItems(itemResponses);
        
        // Calculate totalSkuCount and totalQuantity
        response.setTotalSkuCount(receiptItems.size());
        response.setTotalQuantity(receiptItems.stream()
                .mapToInt(InventoryReceiptItem::getQuantity)
                .sum());

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Object getReceiptStatistics() {
        Map<String, Object> statistics = new HashMap<>();
        statistics.put("totalCount", stockReceiveRepository.count());
        statistics.put("confirmedCount", stockReceiveRepository.countByStatus("CONFIRMED"));
        statistics.put("draftCount", stockReceiveRepository.countByStatus("DRAFT"));
        statistics.put("cancelledCount", stockReceiveRepository.countByStatus("CANCELLED"));
        return statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public String getNextReceiptCode() {
        int currentYear = LocalDate.now().getYear();
        String prefix = "PN-" + currentYear + "-";

        // Use max-of-code (instead of count + 1) so two concurrent calls do
        // not generate the same suffix. Filter by prefix so other code
        // schemes (e.g. REC-*) do not interfere.
        Optional<InventoryReceipt> latest =
                stockReceiveRepository.findTopByReceiptCodeStartingWithOrderByReceiptCodeDesc(prefix);
        if (latest.isEmpty()) {
            return prefix + "001";
        }
        String latestCode = latest.get().getReceiptCode();
        try {
            int number = Integer.parseInt(latestCode.substring(prefix.length()));
            return prefix + String.format("%03d", number + 1);
        } catch (Exception e) {
            return prefix + "001";
        }
    }

    private CostUpdateResult applyReceiptCostAndQuantity(
            InventoryItem inventoryItem,
            ProductVariant productVariant,
            int quantity,
            BigDecimal unitCost,
            User updatedBy) {
        int qtyBefore = inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
        BigDecimal avgCostBefore = inventoryItem.getAverageCost() != null
                ? inventoryItem.getAverageCost()
                : productVariant.getCostPrice();
        avgCostBefore = normalizeMoney(avgCostBefore);
        unitCost = normalizeMoney(unitCost);

        int qtyAfter = qtyBefore + quantity;
        BigDecimal existingStockValue = BigDecimal.valueOf(qtyBefore).multiply(avgCostBefore);
        BigDecimal receivedStockValue = BigDecimal.valueOf(quantity).multiply(unitCost);
        BigDecimal avgCostAfter = qtyAfter <= 0
                ? unitCost
                : existingStockValue
                    .add(receivedStockValue)
                    .divide(BigDecimal.valueOf(qtyAfter), 2, RoundingMode.HALF_UP);

        inventoryItem.setQuantityOnHand(qtyAfter);
        inventoryItem.setAverageCost(avgCostAfter);
        inventoryItem.setUpdatedBy(updatedBy);
        inventoryItemRepository.save(inventoryItem);

        productVariant.setCostPrice(avgCostAfter);
        productVariant.setPrice(avgCostAfter);
        productVariant.setUpdatedBy(updatedBy);
        variantRepository.save(productVariant);

        return new CostUpdateResult(avgCostBefore, avgCostAfter, qtyBefore, qtyAfter);
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record CostUpdateResult(
            BigDecimal avgCostBefore,
            BigDecimal avgCostAfter,
            int qtyBefore,
            int qtyAfter) {
    }

    private OffsetDateTime resolveDocumentTime(LocalDate documentDate) {
        return OffsetDateTime.now();
    }

}
