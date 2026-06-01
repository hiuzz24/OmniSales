package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
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
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final WarehouseRepository warehouseRepository;
    private final ShopRepository shopRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryItemMapper inventoryItemMapper;
    private final InventoryTransactionMapper transactionMapper;

    @Override
    @Transactional
    public InventoryItemResponse createItem(InventoryItemRequest request) {
        Shop shop = shopRepository.findById(request.getShopId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy shop"));
        var warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kho"));
        ProductVariant variant = variantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy variant"));

        InventoryItem item = inventoryItemMapper.toEntity(request);
        item.setShop(shop);
        item.setWarehouse(warehouse);
        item.setVariant(variant);
        return inventoryItemMapper.toResponse(inventoryItemRepository.save(item));
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemResponse getItemById(UUID id) {
        return inventoryItemRepository.findById(id)
                .map(inventoryItemMapper::toResponse)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mục tồn kho: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryItemResponse> getItemsByShop(UUID shopId, int page, int size) {
        Page<InventoryItem> pageResult = inventoryItemRepository.findByShopId(shopId, PageRequest.of(page, size));
        return PageResponse.<InventoryItemResponse>builder()
                .content(pageResult.getContent().stream().map(inventoryItemMapper::toResponse).toList())
                .page(page).size(size)
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst()).last(pageResult.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getLowStockItems(UUID shopId) {
        return inventoryItemRepository.findLowStockItems(shopId)
                .stream().map(inventoryItemMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request) {
        InventoryItem item = inventoryItemRepository
                .findByShopIdAndWarehouseIdAndVariantId(request.getShopId(), request.getWarehouseId(), request.getVariantId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy mục tồn kho"));

        int before = item.getQuantityOnHand();
        int after = before + request.getQuantityChange();
        if (after < 0) throw new IllegalArgumentException("Số lượng tồn kho không thể âm");

        item.setQuantityOnHand(after);
        inventoryItemRepository.save(item);

        InventoryTransaction txn = InventoryTransaction.builder()
                .shop(item.getShop())
                .warehouse(item.getWarehouse())
                .variant(item.getVariant())
                .type(request.getType())
                .referenceType(request.getReferenceType())
                .referenceId(request.getReferenceId())
                .quantityChange(request.getQuantityChange())
                .quantityBefore(before)
                .quantityAfter(after)
                .note(request.getNote())
                .performedAt(OffsetDateTime.now())
                .build();
        return transactionMapper.toResponse(transactionRepository.save(txn));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactionsByShop(UUID shopId, int page, int size) {
        Page<InventoryTransaction> pageResult = transactionRepository.findByShopId(shopId, PageRequest.of(page, size));
        return toTxnPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID shopId, UUID variantId, int page, int size) {
        Page<InventoryTransaction> pageResult = transactionRepository.findByShopIdAndVariantId(shopId, variantId, PageRequest.of(page, size));
        return toTxnPageResponse(pageResult, page, size);
    }

    private PageResponse<InventoryTransactionResponse> toTxnPageResponse(Page<InventoryTransaction> p, int page, int size) {
        return PageResponse.<InventoryTransactionResponse>builder()
                .content(p.getContent().stream().map(transactionMapper::toResponse).toList())
                .page(page).size(size)
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }
}
