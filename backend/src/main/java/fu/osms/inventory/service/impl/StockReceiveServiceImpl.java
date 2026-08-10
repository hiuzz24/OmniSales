package fu.osms.inventory.service.impl;

import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.catalog.util.ProductCostPolicy;
import fu.osms.channel.entity.ChannelProductVariant;
import fu.osms.channel.repository.ChannelProductVariantRepository;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.ManualStockReceiveRequest;
import fu.osms.inventory.dto.request.StockReceiveItemRequest;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveItemResponse;
import fu.osms.inventory.dto.response.StockReceiveResponse;
import fu.osms.inventory.entity.*;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.StockReceiveMapper;
import fu.osms.inventory.repository.*;
import fu.osms.inventory.service.StockReceiveService;
import fu.osms.notification.service.NotificationService;
import fu.osms.purchase.entity.PurchaseOrder;
import fu.osms.purchase.entity.PurchaseOrderItem;
import fu.osms.purchase.enums.PurchaseOrderStatus;
import fu.osms.purchase.repository.PurchaseOrderRepository;
import fu.osms.purchase.service.PurchaseOrderService;
import fu.osms.sync.service.MarketplaceInventoryPropagationService;
import fu.osms.sync.service.MarketplaceWarehouseConsistencyService;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final MarketplaceInventoryPropagationService marketplaceInventoryPropagationService;
    private final MarketplaceWarehouseConsistencyService marketplaceWarehouseConsistencyService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final NotificationService notificationService;
    private final ChannelProductVariantRepository channelProductVariantRepository;

    @Override
    @Transactional
    public StockReceiveResponse createReceipt(StockReceiveRequest request, UUID createdByUserId) {
        if (request.getPurchaseOrderId() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Đơn đặt hàng là bắt buộc.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Receipt must have at least one item");
        }

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdWithDetails(request.getPurchaseOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt hàng."));
        if (!isPoReceivable(purchaseOrder.getStatus())) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION,
                    "Chỉ đơn ở trạng thái Đang giao hàng mới được tạo phiếu nhập kho.");
        }
        validatePurchaseOrderItems(purchaseOrder, request.getItems());

        Warehouse warehouse = purchaseOrder.getWarehouse();
        Supplier supplier = purchaseOrder.getSupplier();

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

        // 5-6. Persist one business line per marketplace SKU. Linked platform
        // variants remain separate mappings and are expanded only when stock is applied.
        ResolvedReceiptLines resolvedLines = resolveLogicalReceiptLines(request.getItems());
        List<StockReceiveItemRequest> itemRequests = resolvedLines.requests();
        List<ProductVariant> variants = resolvedLines.variants();

        // Compute the delivery-batch diff (shortage/surplus) and auto-note for the PO.
        BatchDiff batchDiff = computeBatchDiff(purchaseOrder, null, itemRequests, variants);
        String batchNote = mergeNotes(request.getNotes(), buildBatchNote(batchDiff));

        // 7. Calculate totalCost BEFORE creating receipt
        BigDecimal totalCost = calculateGroupedReceiptTotal(itemRequests, variants);

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
                .purchaseOrder(purchaseOrder)
                .receiptCode(receiptCode)
                .invoiceNumber(receiptCode)
                .status(status)
                .confirmedAt(confirmedAt)
                .receivedAt(receivedAt)
                .notes(batchNote)
                .createdBy(createdByUser)
                .totalCost(totalCost)  // Set totalCost from the beginning
                .build();

        receipt = stockReceiveRepository.save(receipt);

        // Keep the in-memory receipts collection in sync so PO completion checks
        // (confirmedReceiptTotals) can see the just-saved receipt within the same tx.
        purchaseOrder.addReceipt(receipt);

        // 11. Process each item
        List<InventoryReceiptItem> savedItems = new ArrayList<>();
        Set<UUID> changedVariantIds = new HashSet<>();
        Set<String> appliedStockGroups = new HashSet<>();

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
                int quantity = itemReq.getQuantity();
                BigDecimal unitCost = itemReq.getUnitCost();
                List<ProductVariant> sharedVariants = resolveSharedStockVariants(productVariant);
                if (appliedStockGroups.add(sharedStockGroupKey(sharedVariants))) {
                    int sharedQuantityBefore = 0;
                    for (ProductVariant sharedVariant : sharedVariants) {
                        sharedQuantityBefore = Math.max(
                                sharedQuantityBefore,
                                quantityOnHand(ensureInventoryItemWithLock(warehouse, sharedVariant))
                        );
                    }
                    int sharedQuantityAfter = sharedQuantityBefore + quantity;
                    BigDecimal avgCostBefore = resolveSharedCurrentCost(
                            warehouse,
                            sharedVariants,
                            productVariant);
                    BigDecimal avgCostAfter = calculateWeightedAverageCost(
                            sharedQuantityBefore,
                            avgCostBefore,
                            quantity,
                            unitCost);

                    for (ProductVariant sharedVariant : sharedVariants) {
                        InventoryItem inventoryItem = ensureInventoryItemWithLock(warehouse, sharedVariant);
                        int qtyBefore = quantityOnHand(inventoryItem);
                        CostUpdateResult costUpdate = applyReceiptCostAndQuantityTarget(
                                inventoryItem,
                                sharedVariant,
                                sharedQuantityAfter,
                                avgCostBefore,
                                avgCostAfter,
                                unitCost,
                                createdByUser);

                        if (sharedVariant.getId().equals(productVariant.getId())) {
                            receiptItem.setAvgCostBefore(costUpdate.avgCostBefore());
                            receiptItem.setAvgCostAfter(costUpdate.avgCostAfter());
                        }
                        changedVariantIds.add(sharedVariant.getId());

                        InventoryTransaction transaction = InventoryTransaction.builder()
                                .type(InvTxnType.IMPORT)
                                .referenceType("RECEIPT")
                                .referenceId(receipt.getId())
                                .quantityChange(sharedQuantityAfter - qtyBefore)
                                .quantityBefore(qtyBefore)
                                .quantityAfter(sharedQuantityAfter)
                                .unitCost(unitCost)
                                .warehouse(warehouse)
                                .variant(sharedVariant)
                                .performedBy(createdByUser)
                                .performedAt(OffsetDateTime.now())
                                .note(sharedVariants.size() > 1 ? "Shared SKU receipt synchronized" : null)
                                .build();
                        inventoryTransactionRepository.save(transaction);
                    }
                }
            }

            // c. Save receiptItem (for both DRAFT and CONFIRMED). Keep the
            // in-memory items collection in sync so PO completion checks
            // (confirmedReceiptTotals) count this receipt's items — a receipt
            // built with a fresh ArrayList stays "initialized" after save, so
            // Hibernate never re-loads it from the DB.
            receiptItem = stockReceiveItemRepository.save(receiptItem);
            receipt.addItem(receiptItem);
            savedItems.add(receiptItem);

            // d. Create InventoryTransaction (for both DRAFT and CONFIRMED)
            // For DRAFT: just record the transaction without updating actual inventory
            // For CONFIRMED: record the actual inventory changes
            int transactionQtyBefore = 0;
            int transactionQtyAfter = 0;
            
            if ("CONFIRMED".equals(status)) {
                continue;
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
        List<StockReceiveItemResponse> itemResponses = toItemResponses(savedItems, batchDiff.surplusByGroup());
        response.setItems(itemResponses);
        
        ReceiptGroupSummary summary = summarizeReceiptItems(savedItems);
        response.setTotalSkuCount(summary.skuCount());
        response.setTotalQuantity(summary.totalQuantity());
        if ("CONFIRMED".equals(status) && purchaseOrder != null) {
            recordActualReceivedBatch(purchaseOrder, savedItems);
            purchaseOrderService.completeFromReceiptWithResult(purchaseOrder.getId());
            response.setPoCompleted(isPurchaseOrderCompleted(purchaseOrder.getId()));
            notifyMarketplaceSyncChoice(receipt, createdByUser, changedVariantIds);
        }

        return enrichMarketplaceInfo(response);
    }

    @Override
    @Transactional
    public StockReceiveResponse createManualReceipt(ManualStockReceiveRequest request, UUID createdByUserId) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiếu nhập phải có ít nhất một sản phẩm.");
        }

        // Resolve warehouse from request (user explicitly picks it — no PO)
        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy kho nhập."));

        // Supplier is optional
        Supplier supplier = request.getSupplierId() != null
                ? supplierRepository.findById(request.getSupplierId()).orElse(null)
                : null;

        boolean isDraft = Boolean.TRUE.equals(request.getIsDraft());
        if (!isDraft) {
            for (StockReceiveItemRequest item : request.getItems()) {
                if (item.getQuantity() == null || item.getQuantity() <= 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                            "Tất cả sản phẩm phải có số lượng lớn hơn 0 khi xác nhận phiếu nhập.");
                }
                if (item.getUnitCost() == null || item.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                            "Tất cả sản phẩm phải có đơn giá lớn hơn hoặc bằng 0 khi xác nhận phiếu nhập.");
                }
            }
        } else {
            for (StockReceiveItemRequest item : request.getItems()) {
                if (item.getQuantity() == null) {
                    item.setQuantity(0);
                } else if (item.getQuantity() <= 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED, "Số lượng phải lớn hơn 0.");
                }
                if (item.getUnitCost() == null) {
                    item.setUnitCost(BigDecimal.ZERO);
                } else if (item.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED, "Đơn giá phải lớn hơn hoặc bằng 0.");
                }
            }
        }

        ResolvedReceiptLines resolvedLines = resolveLogicalReceiptLines(request.getItems());
        List<StockReceiveItemRequest> itemRequests = resolvedLines.requests();
        List<ProductVariant> variants = resolvedLines.variants();

        BigDecimal totalCost = calculateGroupedReceiptTotal(itemRequests, variants);
        String receiptCode = getNextReceiptCode();
        var createdByUser = userRepository.findById(createdByUserId).orElse(null);
        OffsetDateTime receivedAt = resolveDocumentTime(request.getReceivedAt());

        String status = isDraft ? "DRAFT" : "CONFIRMED";
        OffsetDateTime confirmedAt = isDraft ? null : OffsetDateTime.now();

        InventoryReceipt receipt = InventoryReceipt.builder()
                .warehouse(warehouse)
                .supplier(supplier)
                .purchaseOrder(null)          // manual receipt — no PO
                .receiptCode(receiptCode)
                .invoiceNumber(request.getInvoiceNumber() != null ? request.getInvoiceNumber() : receiptCode)
                .status(status)
                .confirmedAt(confirmedAt)
                .receivedAt(receivedAt)
                .notes(request.getNotes())
                .createdBy(createdByUser)
                .totalCost(totalCost)
                .build();
        receipt = stockReceiveRepository.save(receipt);

        List<InventoryReceiptItem> savedItems = new ArrayList<>();
        Set<UUID> changedVariantIds = new HashSet<>();
        Set<String> appliedStockGroups = new HashSet<>();

        for (int i = 0; i < itemRequests.size(); i++) {
            StockReceiveItemRequest itemReq = itemRequests.get(i);
            ProductVariant productVariant = variants.get(i);

            InventoryReceiptItem receiptItem = InventoryReceiptItem.builder()
                    .receipt(receipt)
                    .variant(productVariant)
                    .quantity(itemReq.getQuantity())
                    .unitCost(itemReq.getUnitCost())
                    .notes(itemReq.getNotes())
                    .build();

            if ("CONFIRMED".equals(status)) {
                int quantity = itemReq.getQuantity();
                BigDecimal unitCost = itemReq.getUnitCost();
                List<ProductVariant> sharedVariants = resolveSharedStockVariants(productVariant);
                if (appliedStockGroups.add(sharedStockGroupKey(sharedVariants))) {
                    int sharedQtyBefore = 0;
                    for (ProductVariant sv : sharedVariants) {
                        sharedQtyBefore = Math.max(sharedQtyBefore,
                                quantityOnHand(ensureInventoryItemWithLock(warehouse, sv)));
                    }
                    int sharedQtyAfter = sharedQtyBefore + quantity;
                    BigDecimal avgCostBefore = resolveSharedCurrentCost(warehouse, sharedVariants, productVariant);
                    BigDecimal avgCostAfter = calculateWeightedAverageCost(
                            sharedQtyBefore, avgCostBefore, quantity, unitCost);

                    for (ProductVariant sv : sharedVariants) {
                        InventoryItem inventoryItem = ensureInventoryItemWithLock(warehouse, sv);
                        int qtyBefore = quantityOnHand(inventoryItem);
                        CostUpdateResult costUpdate = applyReceiptCostAndQuantityTarget(
                                inventoryItem, sv, sharedQtyAfter,
                                avgCostBefore, avgCostAfter, unitCost, createdByUser);
                        if (sv.getId().equals(productVariant.getId())) {
                            receiptItem.setAvgCostBefore(costUpdate.avgCostBefore());
                            receiptItem.setAvgCostAfter(costUpdate.avgCostAfter());
                        }
                        changedVariantIds.add(sv.getId());
                        inventoryTransactionRepository.save(InventoryTransaction.builder()
                                .type(InvTxnType.IMPORT)
                                .referenceType("RECEIPT")
                                .referenceId(receipt.getId())
                                .quantityChange(sharedQtyAfter - qtyBefore)
                                .quantityBefore(qtyBefore)
                                .quantityAfter(sharedQtyAfter)
                                .unitCost(unitCost)
                                .warehouse(warehouse)
                                .variant(sv)
                                .performedBy(createdByUser)
                                .performedAt(OffsetDateTime.now())
                                .note("Manual receipt")
                                .build());
                    }
                }
            }

            receiptItem = stockReceiveItemRepository.save(receiptItem);
            savedItems.add(receiptItem);

            if (!"CONFIRMED".equals(status)) {
                inventoryTransactionRepository.save(InventoryTransaction.builder()
                        .type(InvTxnType.IMPORT)
                        .referenceType("RECEIPT")
                        .referenceId(receipt.getId())
                        .quantityChange(itemReq.getQuantity())
                        .quantityBefore(0)
                        .quantityAfter(itemReq.getQuantity())
                        .unitCost(itemReq.getUnitCost())
                        .warehouse(warehouse)
                        .variant(productVariant)
                        .performedBy(createdByUser)
                        .performedAt(OffsetDateTime.now())
                        .note("DRAFT - Manual receipt - Not applied to inventory")
                        .build());
            }
        }

        StockReceiveResponse response = receiptMapper.toResponse(receipt);
        List<StockReceiveItemResponse> itemResponses = toItemResponses(savedItems);
        response.setItems(itemResponses);
        ReceiptGroupSummary summary = summarizeReceiptItems(savedItems);
        response.setTotalSkuCount(summary.skuCount());
        response.setTotalQuantity(summary.totalQuantity());

        if ("CONFIRMED".equals(status)) {
            notifyMarketplaceSyncChoice(receipt, createdByUser, changedVariantIds);
        }
        return enrichMarketplaceInfo(response);
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
                    List<StockReceiveItemResponse> itemResponses = toItemResponses(items);
                    response.setItems(itemResponses);
                    
                    ReceiptGroupSummary summary = summarizeReceiptItems(items);
                    response.setTotalSkuCount(summary.skuCount());
                    response.setTotalQuantity(summary.totalQuantity());
                    
                    return enrichMarketplaceInfo(response);
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
        List<StockReceiveItemResponse> itemResponses = toItemResponses(items);
        response.setItems(itemResponses);
        if (receipt.getPurchaseOrder() != null) {
            response.setPoCompleted(isPurchaseOrderCompleted(receipt.getPurchaseOrder().getId()));
        }
        
        ReceiptGroupSummary summary = summarizeReceiptItems(items);
        response.setTotalSkuCount(summary.skuCount());
        response.setTotalQuantity(summary.totalQuantity());

        return enrichMarketplaceInfo(response);
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

        if (request.getPurchaseOrderId() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Đơn đặt hàng là bắt buộc.");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Receipt must have at least one item");
        }

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdWithDetails(request.getPurchaseOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy đơn đặt hàng."));
        if (!isPoReceivable(purchaseOrder.getStatus())) {
            throw new AppException(ErrorCode.ORDER_STATUS_INVALID_TRANSITION,
                    "Chỉ đơn ở trạng thái Đang giao hàng, Đang kiểm tra hoặc Đã kiểm tra mới được dùng cho phiếu nhập kho.");
        }
        if (receipt.getPurchaseOrder() != null
                && !receipt.getPurchaseOrder().getId().equals(purchaseOrder.getId())) {
            throw new AppException(ErrorCode.CONFLICT, "Không thể đổi đơn đặt hàng của phiếu nhập kho.");
        }
        validatePurchaseOrderItems(purchaseOrder, request.getItems());
        Warehouse warehouse = purchaseOrder.getWarehouse();
        Supplier supplier = purchaseOrder.getSupplier();

        // 7. For DRAFT: set default values for null quantity/unitCost
        for (StockReceiveItemRequest item : request.getItems()) {
            if (item.getQuantity() == null) {
                item.setQuantity(0);
            }
            if (item.getUnitCost() == null) {
                item.setUnitCost(BigDecimal.ZERO);
            }
        }

        // 8-9. Keep one persisted receipt item per logical marketplace SKU.
        ResolvedReceiptLines resolvedLines = resolveLogicalReceiptLines(request.getItems());
        List<StockReceiveItemRequest> itemRequests = resolvedLines.requests();
        List<ProductVariant> variants = resolvedLines.variants();

        // Recompute the delivery-batch diff (shortage/surplus) and auto-note.
        BatchDiff batchDiff = computeBatchDiff(purchaseOrder, receiptId, itemRequests, variants);
        String batchNote = mergeNotes(request.getNotes(), buildBatchNote(batchDiff));

        // 10. Calculate totalCost
        BigDecimal totalCost = calculateGroupedReceiptTotal(itemRequests, variants);

        // 11. Load updatedBy user
        var updatedByUser = userRepository.findById(updatedByUserId).orElse(null);

        // 12. Update receipt basic info
        OffsetDateTime receivedAt = resolveDocumentTime(request.getReceivedAt());

        receipt.setWarehouse(warehouse);
        receipt.setSupplier(supplier);
        receipt.setPurchaseOrder(purchaseOrder);
        receipt.setInvoiceNumber(receipt.getReceiptCode());
        receipt.setReceivedAt(receivedAt);
        receipt.setNotes(batchNote);
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
        for (int i = 0; i < itemRequests.size(); i++) {
            StockReceiveItemRequest itemReq = itemRequests.get(i);
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
        List<StockReceiveItemResponse> itemResponses = toItemResponses(savedItems, batchDiff.surplusByGroup());
        response.setItems(itemResponses);
        
        ReceiptGroupSummary summary = summarizeReceiptItems(savedItems);
        response.setTotalSkuCount(summary.skuCount());
        response.setTotalQuantity(summary.totalQuantity());

        return enrichMarketplaceInfo(response);
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
            if (item.getUnitCost() == null || item.getUnitCost().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException(ErrorCode.VALIDATION_FAILED, 
                    String.format("Sản phẩm '%s' (SKU: %s) phải có đơn giá lớn hơn hoặc bằng 0",
                    item.getVariant().getProduct().getName(),
                    item.getVariant().getSku()));
            }
        }

        // 5. Load approvedBy user
        var approvedByUser = userRepository.findById(approvedByUserId).orElse(null);

        // 6. Update inventory for each item in the shared marketplace warehouse.
        Warehouse warehouse = marketplaceWarehouseConsistencyService.resolveMasterWarehouse();
        receipt.setWarehouse(warehouse);
        Set<UUID> changedVariantIds = new HashSet<>();
        Set<String> appliedStockGroups = new HashSet<>();
        for (InventoryReceiptItem receiptItem : receiptItems) {
            ProductVariant productVariant = receiptItem.getVariant();
            int quantity = receiptItem.getQuantity();
            BigDecimal unitCost = receiptItem.getUnitCost();
            List<ProductVariant> sharedVariants = resolveSharedStockVariants(productVariant);
            if (!appliedStockGroups.add(sharedStockGroupKey(sharedVariants))) {
                continue;
            }
            int sharedQuantityBefore = sharedVariants.stream()
                    .map(variant -> ensureInventoryItemWithLock(warehouse, variant))
                    .mapToInt(this::quantityOnHand)
                    .max()
                    .orElse(0);
            int sharedQuantityAfter = sharedQuantityBefore + quantity;
            BigDecimal avgCostBefore = resolveSharedCurrentCost(
                    warehouse,
                    sharedVariants,
                    productVariant);
            BigDecimal avgCostAfter = calculateWeightedAverageCost(
                    sharedQuantityBefore,
                    avgCostBefore,
                    quantity,
                    unitCost);

            for (ProductVariant sharedVariant : sharedVariants) {
                InventoryItem inventoryItem = ensureInventoryItemWithLock(warehouse, sharedVariant);
                int qtyBefore = quantityOnHand(inventoryItem);
                CostUpdateResult costUpdate = applyReceiptCostAndQuantityTarget(
                        inventoryItem,
                        sharedVariant,
                        sharedQuantityAfter,
                        avgCostBefore,
                        avgCostAfter,
                        unitCost,
                        approvedByUser);

                if (sharedVariant.getId().equals(productVariant.getId())) {
                    receiptItem.setAvgCostBefore(costUpdate.avgCostBefore());
                    receiptItem.setAvgCostAfter(costUpdate.avgCostAfter());
                }

                InventoryTransaction confirmedTransaction = InventoryTransaction.builder()
                        .type(InvTxnType.IMPORT)
                        .referenceType("RECEIPT")
                        .referenceId(receiptId)
                        .quantityChange(sharedQuantityAfter - qtyBefore)
                        .quantityBefore(qtyBefore)
                        .quantityAfter(sharedQuantityAfter)
                        .unitCost(unitCost)
                        .warehouse(warehouse)
                        .variant(sharedVariant)
                        .performedBy(approvedByUser)
                        .performedAt(OffsetDateTime.now())
                        .note(sharedVariants.size() > 1 ? "Completed from DRAFT - shared SKU synchronized" : "Completed from DRAFT")
                        .build();
                inventoryTransactionRepository.save(confirmedTransaction);
                changedVariantIds.add(sharedVariant.getId());
            }

            stockReceiveItemRepository.save(receiptItem);
        }

        // 7. Update receipt status to CONFIRMED
        receipt.setStatus("CONFIRMED");
        receipt.setConfirmedAt(OffsetDateTime.now());
        receipt.setApprovedBy(approvedByUser);
        receipt = stockReceiveRepository.save(receipt);

        // 8. Build and return response
        StockReceiveResponse response = receiptMapper.toResponse(receipt);
        BatchDiff completeDiff = receipt.getPurchaseOrder() == null
                ? null
                : computeBatchDiff(
                        receipt.getPurchaseOrder(),
                        receipt.getId(),
                        receiptItems.stream()
                                .map(item -> StockReceiveItemRequest.builder()
                                        .variantId(item.getVariant().getId())
                                        .quantity(item.getQuantity())
                                        .unitCost(item.getUnitCost())
                                        .notes(item.getNotes())
                                        .build())
                                .toList(),
                        receiptItems.stream().map(InventoryReceiptItem::getVariant).toList());
        List<StockReceiveItemResponse> itemResponses = toItemResponses(
                receiptItems,
                completeDiff == null ? null : completeDiff.surplusByGroup());
        response.setItems(itemResponses);
        
        ReceiptGroupSummary summary = summarizeReceiptItems(receiptItems);
        response.setTotalSkuCount(summary.skuCount());
        response.setTotalQuantity(summary.totalQuantity());
        if (receipt.getPurchaseOrder() != null) {
            recordActualReceivedBatch(receipt.getPurchaseOrder(), receiptItems);
            purchaseOrderService.completeFromReceipt(receipt.getPurchaseOrder().getId());
            response.setPoCompleted(isPurchaseOrderCompleted(receipt.getPurchaseOrder().getId()));
            notifyMarketplaceSyncChoice(receipt, approvedByUser, changedVariantIds);
        }

        return enrichMarketplaceInfo(response);
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

    @Override
    @Transactional
    public int syncPendingMarketplaceInventory() {
        List<UUID> variantIds = stockReceiveRepository.findConfirmedVariantIdsPendingMarketplaceSync();
        if (variantIds.isEmpty()) {
            return 0;
        }
        Set<UUID> expandedVariantIds = expandSharedVariantIds(variantIds);
        marketplaceInventoryPropagationService.pushAvailableStock(expandedVariantIds);
        return expandedVariantIds.size();
    }

    @Override
    @Transactional
    public int syncReceiptMarketplaceInventory(UUID receiptId) {
        InventoryReceipt receipt = stockReceiveRepository.findById(receiptId)
                .orElseThrow(() -> new AppException(ErrorCode.RECEIPT_NOT_FOUND));
        if (!"CONFIRMED".equals(receipt.getStatus())) {
            throw new AppException(ErrorCode.INVALID_REQUEST, "Chỉ phiếu nhập đã hoàn thành mới được đồng bộ lên sàn.");
        }
        List<UUID> variantIds = stockReceiveRepository.findConfirmedVariantIdsByReceiptId(receiptId);
        if (variantIds.isEmpty()) {
            return 0;
        }
        Set<UUID> expandedVariantIds = expandSharedVariantIds(variantIds);
        marketplaceInventoryPropagationService.pushAvailableStock(expandedVariantIds);
        return expandedVariantIds.size();
    }

    private Set<UUID> expandSharedVariantIds(List<UUID> variantIds) {
        Set<UUID> expanded = new HashSet<>();
        for (ProductVariant variant : variantRepository.findAllById(variantIds)) {
            resolveSharedStockVariants(variant).stream()
                    .map(ProductVariant::getId)
                    .forEach(expanded::add);
        }
        return expanded;
    }

    private void validatePurchaseOrderItems(PurchaseOrder order, List<StockReceiveItemRequest> receiptItems) {
        // A receipt may cover only a subset of the purchase order lines (partial
        // delivery) and quantities may be over or under the ordered amount. Keep
        // only the per-SKU grouping consistency check.
        if (receiptItems == null || receiptItems.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Phiếu nhập phải có ít nhất một sản phẩm.");
        }
        Map<String, Integer> received = new HashMap<>();
        for (StockReceiveItemRequest item : receiptItems) {
            ProductVariant variant = variantRepository.findById(item.getVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
            String groupKey = sharedStockGroupKey(resolveSharedStockVariants(variant));
            Integer previous = received.putIfAbsent(groupKey, item.getQuantity());
            if (previous != null && !Objects.equals(previous, item.getQuantity())) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Các dòng cùng SKU trong phiếu nhập có số lượng không nhất quán.");
            }
        }
    }

    private boolean isPoReceivable(PurchaseOrderStatus status) {
        return status == PurchaseOrderStatus.RECEIVING;
    }

    private boolean isPurchaseOrderCompleted(UUID purchaseOrderId) {
        return purchaseOrderRepository.findById(purchaseOrderId)
                .map(order -> order.getStatus() == PurchaseOrderStatus.COMPLETED)
                .orElse(false);
    }

    private BatchDiff computeBatchDiff(
            PurchaseOrder order,
            UUID excludeReceiptId,
            List<StockReceiveItemRequest> itemRequests,
            List<ProductVariant> variants) {
        Map<String, Integer> batchQtyByGroup = new HashMap<>();
        for (int i = 0; i < itemRequests.size(); i++) {
            String key = sharedStockGroupKey(resolveSharedStockVariants(variants.get(i)));
            Integer qty = itemRequests.get(i).getQuantity();
            batchQtyByGroup.merge(key, qty == null ? 0 : qty, Integer::sum);
        }
        return computeBatchDiff(order, excludeReceiptId, batchQtyByGroup);
    }

    private BatchDiff computeBatchDiff(
            PurchaseOrder order, UUID excludeReceiptId, Map<String, Integer> batchQtyByGroup) {
        Map<String, Integer> orderedByGroup = new LinkedHashMap<>();
        Map<String, String> groupLabels = new HashMap<>();
        for (PurchaseOrderItem item : order.getItems()) {
            String key = sharedStockGroupKey(resolveSharedStockVariants(item.getVariant()));
            orderedByGroup.putIfAbsent(key, item.getQuantity());
            groupLabels.putIfAbsent(key, productLabel(item.getVariant()));
        }

        Map<String, Integer> priorByGroup = new HashMap<>();
        String priorReceiptCode = null;
        for (InventoryReceipt receipt : order.getReceipts()) {
            if (excludeReceiptId != null && excludeReceiptId.equals(receipt.getId())) {
                continue;
            }
            if (!"CONFIRMED".equals(receipt.getStatus())) {
                continue;
            }
            for (InventoryReceiptItem item : receipt.getItems()) {
                String key = sharedStockGroupKey(resolveSharedStockVariants(item.getVariant()));
                priorByGroup.merge(key, item.getQuantity(), Integer::sum);
            }
            priorReceiptCode = receipt.getReceiptCode();
        }

        Map<String, Integer> shortageByGroup = new LinkedHashMap<>();
        Map<String, Integer> surplusByGroup = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : orderedByGroup.entrySet()) {
            String key = entry.getKey();
            int orderedQty = entry.getValue();
            int batchQty = batchQtyByGroup.getOrDefault(key, 0);
            int remaining = orderedQty - priorByGroup.getOrDefault(key, 0);
            if (batchQty > remaining) {
                surplusByGroup.put(key, batchQty - remaining);
            }
            if (batchQty < remaining) {
                shortageByGroup.put(key, remaining - batchQty);
            }
        }
        return new BatchDiff(shortageByGroup, surplusByGroup, groupLabels, priorReceiptCode);
    }

    private String buildBatchNote(BatchDiff diff) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : diff.shortageByGroup().entrySet()) {
            parts.add(diff.groupLabels().getOrDefault(entry.getKey(), "Sản phẩm") + " thiếu " + entry.getValue());
        }
        for (Map.Entry<String, Integer> entry : diff.surplusByGroup().entrySet()) {
            parts.add(diff.groupLabels().getOrDefault(entry.getKey(), "Sản phẩm") + " thừa " + entry.getValue());
        }
        String summary = parts.isEmpty() ? "Nhận đủ theo đơn mua." : String.join("; ", parts);
        if (diff.priorReceiptCode() == null) {
            return summary;
        }
        return "Tạo thêm từ phiếu nhập " + diff.priorReceiptCode() + ". " + summary;
    }

    private String mergeNotes(String userNotes, String batchNote) {
        if (userNotes == null || userNotes.isBlank()) {
            return batchNote;
        }
        return batchNote + " | " + userNotes.trim();
    }

    private String productLabel(ProductVariant variant) {
        String name = variant.getProduct().getName();
        return variant.getName() != null && !variant.getName().isBlank()
                ? name + " (" + variant.getName() + ")"
                : name;
    }

    private StockReceiveResponse enrichMarketplaceInfo(StockReceiveResponse response) {
        if (response == null || response.getId() == null) {
            return response;
        }
        List<UUID> receiptVariantIds = stockReceiveItemRepository.findByReceiptId(response.getId()).stream()
                .map(item -> item.getVariant().getId()).distinct().toList();
        if (receiptVariantIds.isEmpty()) {
            response.setMarketplacePlatforms(List.of());
            response.setMarketplaceSyncAvailable(false);
            return response;
        }
        List<UUID> variantIds = new ArrayList<>(expandSharedVariantIds(receiptVariantIds));
        List<String> platforms = channelProductVariantRepository
                .findActiveByVariantIdInWithChannel(variantIds).stream()
                .map(ChannelProductVariant::getChannelProduct)
                .filter(java.util.Objects::nonNull)
                .map(item -> item.getChannel())
                .filter(java.util.Objects::nonNull)
                .map(item -> item.getPlatform())
                .filter(platform -> platform == PlatformType.SHOPIFY
                        || platform == PlatformType.LAZADA || platform == PlatformType.TIKTOK)
                .map(Enum::name).distinct().toList();
        response.setMarketplacePlatforms(platforms);
        // Only mark sync-available when the receipt is CONFIRMED AND has variants
        // whose channel_product_variants.lastSyncedAt is still behind the receipt's confirmedAt/updatedAt.
        boolean hasPendingSync = "CONFIRMED".equals(response.getStatus())
                && !platforms.isEmpty()
                && stockReceiveRepository.countPendingMarketplaceSyncVariantsByReceiptId(response.getId()) > 0;
        response.setMarketplaceSyncAvailable(hasPendingSync);
        return response;
    }

    private List<StockReceiveItemResponse> toItemResponses(List<InventoryReceiptItem> items) {
        return toItemResponses(items, null);
    }

    /**
     * Record the actually-received quantity of the latest batch onto the matching
     * purchase-order line(s) (purchase_order_items.actual_quantity). This value is
     * exposed to the receiving UI so it can prefill the "Số lượng thực tế" input
     * when the next batch for the same PO is created.
     */
    private void recordActualReceivedBatch(PurchaseOrder purchaseOrder, List<InventoryReceiptItem> batchItems) {
        if (purchaseOrder == null || batchItems == null || batchItems.isEmpty()
                || purchaseOrder.getItems() == null || purchaseOrder.getItems().isEmpty()) {
            return;
        }
        Map<UUID, Integer> batchByVariant = new HashMap<>();
        for (InventoryReceiptItem item : batchItems) {
            if (item.getVariant() == null || item.getQuantity() == null) {
                continue;
            }
            batchByVariant.merge(item.getVariant().getId(), item.getQuantity(), Integer::sum);
        }
        if (batchByVariant.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (PurchaseOrderItem poItem : purchaseOrder.getItems()) {
            if (poItem.getVariant() == null) {
                continue;
            }
            Integer batchQty = batchByVariant.get(poItem.getVariant().getId());
            if (batchQty == null) {
                continue;
            }
            int previous = poItem.getActualQuantity() == null ? 0 : poItem.getActualQuantity();
            int accumulated = previous + batchQty;
            if (accumulated != previous) {
                poItem.setActualQuantity(accumulated);
                changed = true;
            }
        }
        if (changed) {
            purchaseOrderRepository.save(purchaseOrder);
        }
    }

    private List<StockReceiveItemResponse> toItemResponses(
            List<InventoryReceiptItem> items, Map<String, Integer> surplusByGroup) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        Map<String, InventoryReceiptItem> logicalItems = new LinkedHashMap<>();
        Map<String, List<ProductVariant>> sharedVariantsByGroup = new LinkedHashMap<>();
        for (InventoryReceiptItem item : items) {
            List<ProductVariant> sharedVariants = resolveSharedStockVariants(item.getVariant());
            String groupKey = sharedStockGroupKey(sharedVariants);
            logicalItems.putIfAbsent(groupKey, item);
            sharedVariantsByGroup.putIfAbsent(groupKey, sharedVariants);
        }

        List<UUID> variantIds = sharedVariantsByGroup.values().stream()
                .flatMap(List::stream)
                .map(ProductVariant::getId)
                .distinct()
                .toList();
        Map<UUID, List<ChannelProductVariant>> mappingsByVariant =
                channelProductVariantRepository.findActiveByVariantIdInWithChannel(variantIds).stream()
                        .filter(mapping -> mapping.getChannelProduct() != null
                                && mapping.getChannelProduct().getChannel() != null)
                        .collect(java.util.stream.Collectors.groupingBy(
                                mapping -> mapping.getVariant().getId(),
                                LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));

        return logicalItems.entrySet().stream().map(entry -> {
            InventoryReceiptItem item = entry.getValue();
            StockReceiveItemResponse response = receiptMapper.toItemResponse(item);
            List<ChannelProductVariant> mappings = sharedVariantsByGroup
                    .getOrDefault(entry.getKey(), List.of(item.getVariant())).stream()
                    .flatMap(variant -> mappingsByVariant
                            .getOrDefault(variant.getId(), List.of()).stream())
                    .toList();
            String marketplaceSku = mappings.stream()
                    .map(ChannelProductVariant::getExternalSku)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(item.getVariant().getSku());
            List<PlatformType> platforms = mappings.stream()
                    .map(mapping -> mapping.getChannelProduct().getChannel().getPlatform())
                    .filter(platform -> platform == PlatformType.SHOPIFY
                            || platform == PlatformType.LAZADA
                            || platform == PlatformType.TIKTOK)
                    .distinct()
                    .sorted(java.util.Comparator.comparing(Enum::name))
                    .toList();
            response.setSku(marketplaceSku);
            response.setMarketplaceSku(marketplaceSku);
            response.setPlatforms(platforms);
            response.setSurplusQuantity(surplusByGroup == null
                    ? 0
                    : surplusByGroup.getOrDefault(entry.getKey(), 0));
            return response;
        }).toList();
    }

    private void notifyMarketplaceSyncChoice(InventoryReceipt receipt, User user, Set<UUID> changedVariantIds) {
        if (user == null || changedVariantIds == null || changedVariantIds.isEmpty()) {
            return;
        }
        StockReceiveResponse response = enrichMarketplaceInfo(receiptMapper.toResponse(receipt));
        if (!Boolean.TRUE.equals(response.getMarketplaceSyncAvailable())) {
            return;
        }
        notificationService.createNotification(user.getId(), "SYNC", "Đồng bộ tồn có thể bán và giá lên sàn?",
                receipt.getReceiptCode() + " đã hoàn thành. Sản phẩm đang bán trên "
                        + String.join(", ", response.getMarketplacePlatforms())
                        + ". Hãy xác nhận đồng bộ tồn có thể bán và giá bán.",
                "RECEIPT", receipt.getId());
    }

    private CostUpdateResult applyReceiptCostAndQuantityTarget(
            InventoryItem inventoryItem,
            ProductVariant productVariant,
            int quantityAfter,
            BigDecimal avgCostBefore,
            BigDecimal avgCostAfter,
            BigDecimal receivedUnitCost,
            User updatedBy) {
        int qtyBefore = quantityOnHand(inventoryItem);
        avgCostBefore = normalizeMoney(avgCostBefore);
        avgCostAfter = normalizeMoney(avgCostAfter);
        BigDecimal sellingPrice = normalizeMoney(receivedUnitCost);

        inventoryItem.setQuantityOnHand(quantityAfter);
        inventoryItem.setAverageCost(avgCostAfter);
        inventoryItem.setUpdatedBy(updatedBy);
        inventoryItemRepository.save(inventoryItem);

        productVariant.setPrice(sellingPrice);
        productVariant.setCostPrice(avgCostAfter);
        productVariant.setUpdatedBy(updatedBy);
        variantRepository.save(productVariant);

        return new CostUpdateResult(avgCostBefore, avgCostAfter, qtyBefore, quantityAfter);
    }

    private BigDecimal calculateWeightedAverageCost(
            int currentQuantity,
            BigDecimal currentCost,
            int receivedQuantity,
            BigDecimal receivedUnitCost) {
        BigDecimal normalizedCurrentCost = normalizeMoney(currentCost);
        BigDecimal normalizedReceivedCost = normalizeMoney(receivedUnitCost);
        if (receivedQuantity <= 0) {
            return normalizedCurrentCost;
        }
        int weightedQuantity = currentQuantity + receivedQuantity;
        if (weightedQuantity <= 0) {
            return normalizedReceivedCost;
        }
        BigDecimal existingStockValue = BigDecimal.valueOf(currentQuantity)
                .multiply(normalizedCurrentCost);
        BigDecimal receivedStockValue = BigDecimal.valueOf(receivedQuantity)
                .multiply(normalizedReceivedCost);
        return existingStockValue
                .add(receivedStockValue)
                .divide(BigDecimal.valueOf(weightedQuantity), 2, RoundingMode.HALF_UP);
    }

    private InventoryItem ensureInventoryItemWithLock(Warehouse warehouse, ProductVariant productVariant) {
        inventoryItemRepository.findByWarehouseIdAndVariantId(warehouse.getId(), productVariant.getId())
                .orElseGet(() -> inventoryItemRepository.save(InventoryItem.builder()
                        .warehouse(warehouse)
                        .variant(productVariant)
                        .quantityOnHand(0)
                        .reservedQuantity(0)
                        .averageCost(resolveCurrentCost(null, productVariant))
                        .build()));
        return inventoryItemRepository
                .findByWarehouseIdAndVariantIdWithLock(warehouse.getId(), productVariant.getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));
    }

    private BigDecimal resolveCurrentCost(InventoryItem inventoryItem, ProductVariant productVariant) {
        BigDecimal inventoryAverageCost = inventoryItem == null ? null : inventoryItem.getAverageCost();
        if (ProductCostPolicy.isPositive(inventoryAverageCost)) {
            return normalizeMoney(inventoryAverageCost);
        }
        return normalizeMoney(ProductCostPolicy.initialCost(
                productVariant.getCostPrice(),
                productVariant.getPrice()));
    }

    private BigDecimal resolveSharedCurrentCost(
            Warehouse warehouse,
            List<ProductVariant> sharedVariants,
            ProductVariant preferredVariant) {
        List<ProductVariant> orderedVariants = new ArrayList<>();
        orderedVariants.add(preferredVariant);
        sharedVariants.stream()
                .filter(variant -> !variant.getId().equals(preferredVariant.getId()))
                .forEach(orderedVariants::add);

        for (ProductVariant variant : orderedVariants) {
            BigDecimal averageCost = ensureInventoryItemWithLock(warehouse, variant).getAverageCost();
            if (ProductCostPolicy.isPositive(averageCost)) {
                return normalizeMoney(averageCost);
            }
        }
        for (ProductVariant variant : orderedVariants) {
            if (ProductCostPolicy.isPositive(variant.getCostPrice())) {
                return normalizeMoney(variant.getCostPrice());
            }
        }
        for (ProductVariant variant : orderedVariants) {
            if (ProductCostPolicy.isPositive(variant.getPrice())) {
                return normalizeMoney(variant.getPrice());
            }
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private List<ProductVariant> resolveSharedStockVariants(ProductVariant productVariant) {
        Set<String> externalSkuKeys = externalSkuKeys(
                channelProductVariantRepository.findActiveByVariantIdWithChannel(productVariant.getId()));
        if (externalSkuKeys.size() != 1) {
            return List.of(productVariant);
        }

        Map<UUID, ProductVariant> variantsById = new LinkedHashMap<>();
        variantsById.put(productVariant.getId(), productVariant);
        channelProductVariantRepository.findActiveByNormalizedExternalSkuInWithVariant(
                        new ArrayList<>(externalSkuKeys)).stream()
                .map(ChannelProductVariant::getVariant)
                .forEach(variant -> variantsById.putIfAbsent(variant.getId(), variant));
        return new ArrayList<>(variantsById.values());
    }

    private ResolvedReceiptLines resolveLogicalReceiptLines(List<StockReceiveItemRequest> requests) {
        Map<String, Integer> lineIndexByGroup = new LinkedHashMap<>();
        List<StockReceiveItemRequest> logicalRequests = new ArrayList<>();
        List<ProductVariant> logicalVariants = new ArrayList<>();
        List<ReceiptLineTerms> logicalTerms = new ArrayList<>();

        for (StockReceiveItemRequest request : requests) {
            ProductVariant variant = variantRepository.findById(request.getVariantId())
                    .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));
            if (Boolean.FALSE.equals(variant.getIsActive()) || variant.getDeletedAt() != null) {
                throw new AppException(ErrorCode.VARIANT_NOT_FOUND);
            }

            List<ProductVariant> sharedVariants = resolveSharedStockVariants(variant);
            String groupKey = sharedStockGroupKey(sharedVariants);
            BigDecimal unitCost = normalizeMoney(request.getUnitCost());
            Integer existingIndex = lineIndexByGroup.get(groupKey);
            if (existingIndex != null) {
                ReceiptLineTerms terms = logicalTerms.get(existingIndex);
                if (!terms.matches(request.getQuantity(), unitCost)) {
                    throw new AppException(ErrorCode.VALIDATION_FAILED,
                            "Các sản phẩm con liên kết cùng SKU phải có cùng số lượng và đơn giá.");
                }
                continue;
            }

            String canonicalSku = canonicalMarketplaceSku(sharedVariants, variant);
            ProductVariant representative = sharedVariants.stream()
                    .filter(item -> canonicalSku.equalsIgnoreCase(item.getSku()))
                    .findFirst()
                    .orElse(variant);
            lineIndexByGroup.put(groupKey, logicalRequests.size());
            logicalRequests.add(request);
            logicalVariants.add(representative);
            logicalTerms.add(new ReceiptLineTerms(request.getQuantity(), unitCost));
        }
        return new ResolvedReceiptLines(logicalRequests, logicalVariants);
    }

    private String canonicalMarketplaceSku(List<ProductVariant> variants, ProductVariant fallback) {
        List<UUID> variantIds = variants.stream().map(ProductVariant::getId).toList();
        List<ChannelProductVariant> mappings =
                channelProductVariantRepository.findActiveByVariantIdInWithChannel(variantIds);
        Set<String> externalSkuKeys = externalSkuKeys(mappings);
        if (externalSkuKeys.size() != 1) {
            return fallback.getSku();
        }
        String canonicalKey = externalSkuKeys.iterator().next();
        return mappings.stream()
                .map(ChannelProductVariant::getExternalSku)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> canonicalKey.equals(normalizeSku(value)))
                .findFirst()
                .orElse(fallback.getSku());
    }

    private BigDecimal calculateGroupedReceiptTotal(
            List<StockReceiveItemRequest> itemRequests,
            List<ProductVariant> variants) {
        Map<String, ReceiptLineTerms> termsByGroup = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < itemRequests.size(); index++) {
            StockReceiveItemRequest request = itemRequests.get(index);
            String groupKey = sharedStockGroupKey(resolveSharedStockVariants(variants.get(index)));
            BigDecimal unitCost = normalizeMoney(request.getUnitCost());
            ReceiptLineTerms existing = termsByGroup.putIfAbsent(
                    groupKey,
                    new ReceiptLineTerms(request.getQuantity(), unitCost));
            if (existing == null) {
                total = total.add(BigDecimal.valueOf(request.getQuantity()).multiply(unitCost));
            } else if (!existing.matches(request.getQuantity(), unitCost)) {
                throw new AppException(ErrorCode.VALIDATION_FAILED,
                        "Các sản phẩm con liên kết cùng SKU phải có cùng số lượng và đơn giá.");
            }
        }
        return total;
    }

    private ReceiptGroupSummary summarizeReceiptItems(List<InventoryReceiptItem> items) {
        Set<String> groups = new HashSet<>();
        int totalQuantity = 0;
        for (InventoryReceiptItem item : items) {
            String groupKey = sharedStockGroupKey(resolveSharedStockVariants(item.getVariant()));
            if (groups.add(groupKey)) {
                totalQuantity += item.getQuantity() == null ? 0 : item.getQuantity();
            }
        }
        return new ReceiptGroupSummary(groups.size(), totalQuantity);
    }

    private String sharedStockGroupKey(List<ProductVariant> variants) {
        Set<String> externalSkuKeys = externalSkuKeys(
                channelProductVariantRepository.findActiveByVariantIdInWithChannel(
                        variants.stream().map(ProductVariant::getId).toList()));
        if (externalSkuKeys.size() == 1) {
            return "external:" + externalSkuKeys.iterator().next();
        }
        return variants.stream()
                .map(ProductVariant::getId)
                .map(UUID::toString)
                .sorted()
                .map(id -> "variant:" + id)
                .collect(Collectors.joining("|"));
    }

    private String normalizeSku(String sku) {
        return sku == null ? "" : sku.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> externalSkuKeys(List<ChannelProductVariant> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return Set.of();
        }
        return mappings.stream()
                .map(ChannelProductVariant::getExternalSku)
                .filter(value -> value != null && !value.isBlank())
                .map(this::normalizeSku)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int quantityOnHand(InventoryItem inventoryItem) {
        return inventoryItem.getQuantityOnHand() != null ? inventoryItem.getQuantityOnHand() : 0;
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

    private record ReceiptLineTerms(Integer quantity, BigDecimal unitCost) {
        private boolean matches(Integer otherQuantity, BigDecimal otherUnitCost) {
            return Objects.equals(quantity, otherQuantity) && unitCost.compareTo(otherUnitCost) == 0;
        }
    }

    private record ResolvedReceiptLines(
            List<StockReceiveItemRequest> requests,
            List<ProductVariant> variants) {
    }

    private record ReceiptGroupSummary(int skuCount, int totalQuantity) {
    }

    private record BatchDiff(
            Map<String, Integer> shortageByGroup,
            Map<String, Integer> surplusByGroup,
            Map<String, String> groupLabels,
            String priorReceiptCode) {
    }

    private OffsetDateTime resolveDocumentTime(LocalDate documentDate) {
        return OffsetDateTime.now();
    }

}
