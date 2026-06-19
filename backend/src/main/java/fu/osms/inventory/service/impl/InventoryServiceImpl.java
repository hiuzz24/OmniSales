package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.dto.response.StockSummaryDTO;
import fu.osms.inventory.entity.InventoryItem;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.mapper.InventoryItemMapper;
import fu.osms.inventory.mapper.InventoryTransactionMapper;
import fu.osms.inventory.repository.InventoryItemRepository;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.WarehouseRepository;
import fu.osms.inventory.service.InventoryService;
import fu.osms.catalog.entity.ProductVariant;
import fu.osms.catalog.repository.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final InventoryTransactionMapper transactionMapper;

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
}
