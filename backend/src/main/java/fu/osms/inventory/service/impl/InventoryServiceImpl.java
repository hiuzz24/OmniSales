package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.Category;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryItemUpdateRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryDetailDTO;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.AvailableVariantDTOMapper;
import fu.osms.inventory.mapper.InventoryDetailMapper;
import fu.osms.inventory.mapper.InventoryItemMapper;
import fu.osms.inventory.mapper.InventoryTransactionMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.InventoryService;
import fu.osms.inventory.service.InventoryAlertService;
import fu.osms.inventory.dto.response.AvailableVariantDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final UserRepository userRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final CategoryRepository categoryRepository;
    private final InventoryDetailMapper inventoryDetailMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final AvailableVariantDTOMapper availableVariantDTOMapper;
    private final InventoryAlertService inventoryAlertService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getAllInventoryItems(PageRequest pageRequest, int page, int size) {
        Page<InventoryItem> inventoryItemPage = inventoryItemRepository.findAllWithVariantRelationships(pageRequest);

        List<InventoryItemResponse> dtoList = inventoryItemMapper.toResponseList(inventoryItemPage.getContent());

        return PageResponse.<InventoryItemResponse>builder()
                .content(dtoList)
                .page(inventoryItemPage.getNumber())
                .size(inventoryItemPage.getSize())
                .totalElements(inventoryItemPage.getTotalElements())
                .totalPages(inventoryItemPage.getTotalPages())
                .first(inventoryItemPage.isFirst())
                .last(inventoryItemPage.isLast())
                .build();
    }

    @Override
    @Transactional
    public InventoryItemResponse createItem(InventoryItemRequest request) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemById(UUID id) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getItems(UUID warehouseId, int page, int size) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        Page<InventoryItem> items = inventoryItemRepository.findByWarehouseId(warehouseId, PageRequest.of(page, size));
        List<InventoryItemResponse> content = items.getContent().stream()
                .map(inventoryItemMapper::toResponse)
                .toList();

        return PageResponse.<InventoryItemResponse>builder()
                .content(content)
                .page(items.getNumber())
                .size(items.getSize())
                .totalElements(items.getTotalElements())
                .totalPages(items.getTotalPages())
                .first(items.isFirst())
                .last(items.isLast())
                .build();
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getItemByWarehouseAndVariant(UUID warehouseId, UUID variantId) {
        InventoryItem item = inventoryItemRepository.findByWarehouseIdAndVariantId(warehouseId, variantId)
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND));
        return inventoryItemMapper.toResponse(item);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getLowStockItems() {
        return inventoryItemMapper.toResponseList(inventoryItemRepository.findLowStockItems());
    }

    @Override
    @Transactional
    public InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request) {
        if (request.getQuantityChange() == null || request.getQuantityChange() == 0) {
            throw new AppException(ErrorCode.VALIDATION_FAILED, "Adjustment quantity must be greater than 0");
        }
        if (!warehouseRepository.existsById(request.getWarehouseId())) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }
        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.VARIANT_NOT_FOUND));

        InventoryItem item = inventoryItemRepository
                .findByWarehouseIdAndVariantIdWithLock(request.getWarehouseId(), request.getVariantId())
                .orElseThrow(() -> new AppException(ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                        "Product is not available in selected warehouse."));

        int quantityBefore = item.getQuantityOnHand() != null ? item.getQuantityOnHand() : 0;
        int quantityAfter = quantityBefore + request.getQuantityChange();
        if (quantityAfter < 0) {
            throw new AppException(ErrorCode.NEGATIVE_STOCK_NOT_ALLOWED,
                    "Adjustment would result in negative inventory. Please reduce quantity.");
        }

        User currentUser = getCurrentUser();
        item.setQuantityOnHand(quantityAfter);
        item.setUpdatedBy(currentUser);
        inventoryItemRepository.save(item);
        inventoryAlertService.notifyLowStockAfterStockChange(item);

        InventoryTransaction transaction = InventoryTransaction.builder()
                .warehouse(item.getWarehouse())
                .variant(variant)
                .type(request.getType())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .quantityChange(request.getQuantityChange())
                .quantityBefore(quantityBefore)
                .quantityAfter(quantityAfter)
                .unitCost(item.getAverageCost())
                .note(request.getNote())
                .performedBy(currentUser)
                .performedAt(OffsetDateTime.now())
                .build();
        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactions(int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID variantId, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    private PageResponse<InventoryTransactionResponse> toTxnPageResponse(Page<InventoryTransaction> p, int page, int size) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StockSummaryDTO> getStockSummary(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<InventoryItem> items = inventoryItemRepository.findByVariantIdIn(variantIds);

        return items.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getVariant().getId(),
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    int totalAvailable = list.stream().mapToInt(i -> i.getAvailableQuantity() != null ? i.getAvailableQuantity() : 0).sum();
                                    int totalOnHand = list.stream().mapToInt(i -> i.getQuantityOnHand() != null ? i.getQuantityOnHand() : 0).sum();
                                    int totalReserved = list.stream().mapToInt(i -> i.getReservedQuantity() != null ? i.getReservedQuantity() : 0).sum();
                                    return StockSummaryDTO.builder()
                                            .availableQuantity(totalAvailable)
                                            .quantityOnHand(totalOnHand)
                                            .reservedQuantity(totalReserved)
                                            .build();
                                }
                        )
                ));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getInventoryByCategoryId(UUID categoryId, PageRequest pageRequest, int page, int size) {
        List<UUID> allCategoryIds = new ArrayList<>();

        findAllChildIds(categoryId, allCategoryIds);
        Page<InventoryItem> inventoryPage = inventoryItemRepository.findByCategoryIdIn(allCategoryIds, pageRequest);

        List<InventoryItemResponse> content = inventoryItemMapper.toResponseList(inventoryPage.getContent());

        return PageResponse.<InventoryItemResponse>builder()
                .content(content)
                .page(inventoryPage.getNumber())
                .size(inventoryPage.getSize())
                .totalElements(inventoryPage.getTotalElements())
                .totalPages(inventoryPage.getTotalPages())
                .first(inventoryPage.isFirst())
                .last(inventoryPage.isLast())
                .build();
    }

    @Override
    public void findAllChildIds(UUID parentId, List<UUID> resultList) {
        List<Category> children = categoryRepository.findByParentId(parentId);
        resultList.add(parentId);
        for (Category child : children) {
            resultList.add(child.getId());
            findAllChildIds(child.getId(), resultList);
        }

    }

    @Override
    @Transactional
    public InventoryDetailDTO getInventoryItemDetail(UUID inventoryItemId) {
        InventoryItem inventoryItem = inventoryItemRepository.findDetailById(inventoryItemId)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy thông tin tồn kho yêu cầu"));
        InventoryDetailDTO dto = inventoryDetailMapper.toDetailDTO(inventoryItem);

        UUID variantId = inventoryItem.getVariant().getId();

        dto.setLastUpdatedAt(inventoryItem.getUpdatedAt());

        transactionRepository.findFirstByVariantIdOrderByPerformedAtDesc(variantId)
                .ifPresent(txn -> {
                    if (dto.getLastUpdatedAt() == null || txn.getPerformedAt().isAfter(dto.getLastUpdatedAt())) {
                        dto.setLastUpdatedAt(txn.getPerformedAt());
                    }
                });

        transactionRepository.findFirstByVariantIdAndTypeOrderByPerformedAtDesc(variantId, InvTxnType.IMPORT)
                .ifPresent(txn -> dto.setLastImportedAt(txn.getPerformedAt()));

        return dto;
    }

    @Override
    @Transactional
    public InventoryDetailDTO updateInventoryDetail(UUID inventoryItemId, InventoryItemUpdateRequest request) {
        InventoryItem inventoryItem = inventoryItemRepository.findById(inventoryItemId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin tồn kho yêu cầu"));

        // If warehouse is changed, check constraint
        if (request.getWarehouseId() != null && !request.getWarehouseId().equals(inventoryItem.getWarehouse().getId())) {
            Optional<InventoryItem> existing = inventoryItemRepository.findByWarehouseIdAndVariantId(request.getWarehouseId(), inventoryItem.getVariant().getId());
            if (existing.isPresent() && !existing.get().getId().equals(inventoryItemId)) {
                throw new IllegalArgumentException("Sản phẩm này đã tồn tại trong kho hàng được chọn.");
            }
            fu.osms.inventory.entity.Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                    .orElseThrow(() -> new IllegalArgumentException("Kho hàng không tồn tại."));
            inventoryItem.setWarehouse(warehouse);
        }

        if (request.getQuantityOnHand() != null) {
            inventoryItem.setQuantityOnHand(request.getQuantityOnHand());
        }
        if (request.getAverageCost() != null) {
            inventoryItem.setAverageCost(request.getAverageCost());
        }

        ProductVariant variant = inventoryItem.getVariant();
        if (variant != null) {
            if (request.getProductVariantName() != null) {
                variant.setName(request.getProductVariantName());
            }
            if (request.getPrice() != null) {
                variant.setPrice(request.getPrice());
            }
            if (request.getAverageCost() != null) {
                variant.setCostPrice(request.getAverageCost());
            }
            variantRepository.save(variant);
        }

        User currentUser = getCurrentUser();
        inventoryItem.setUpdatedBy(currentUser);
        inventoryItemRepository.save(inventoryItem);

        // Fetch refreshed details to return
        return getInventoryItemDetail(inventoryItemId);
    }

    @Override
    public List<AvailableVariantDTO> getAvailableVariantsByWarehouse(UUID warehouseId) {
        if (!warehouseRepository.existsById(warehouseId)) {
            throw new AppException(ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        List<InventoryItem> inventoryItems = inventoryItemRepository.findByWarehouseId(warehouseId);

        return inventoryItems.stream()
                .map(item -> availableVariantDTOMapper.toAvailableDto(item.getVariant(), item))
                .collect(Collectors.toList());
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }


}
