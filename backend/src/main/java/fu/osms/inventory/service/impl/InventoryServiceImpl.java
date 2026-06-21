package fu.osms.inventory.service.impl;

import fu.osms.catalog.entity.Category;
import fu.osms.catalog.repository.CategoryRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryDetailDTO;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.InventoryDetailMapper;
import fu.osms.inventory.mapper.InventoryItemMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.InventoryService;
import fu.osms.catalog.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final CategoryRepository categoryRepository;
    private final InventoryDetailMapper inventoryDetailMapper;
    //private final InventoryTransactionMapper transactionMapper;

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
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemById(UUID id) {
        throw new UnsupportedOperationException("Chưa code");
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
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactions(int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID variantId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    private PageResponse<InventoryTransactionResponse> toTxnPageResponse(Page<InventoryTransaction> p, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
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
    @Transactional(readOnly = true)
    public InventoryDetailDTO getInventoryItemDetail(UUID inventoryItemId) {
        InventoryItem inventoryItem = inventoryItemRepository.findDetailById(inventoryItemId)
                .orElseThrow(() -> new RuntimeException(
                        "Không tìm thấy thông tin tồn kho yêu cầu"));
        InventoryDetailDTO dto = inventoryDetailMapper.toDetailDTO(inventoryItem);

        UUID variantId = inventoryItem.getVariant().getId();

        transactionRepository.findFirstByVariantIdOrderByPerformedAtDesc(variantId)
                .ifPresent(txn -> dto.setLastUpdatedAt(txn.getPerformedAt()));

        transactionRepository.findFirstByVariantIdAndTypeOrderByPerformedAtDesc(variantId, InvTxnType.IMPORT)
                .ifPresent(txn -> dto.setLastImportedAt(txn.getPerformedAt()));

        return dto;
    }
}
