package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import fu.osms.inventory.dto.request.InventoryItemUpdateRequest;
import fu.osms.common.enums.PlatformType;
import org.springframework.data.domain.PageRequest;

public interface InventoryService {

    PageResponse<InventoryItemResponse> getAllInventoryItems(PageRequest pageRequest, int page, int size, UUID channelId, boolean localOnly, String keyword, String status, UUID warehouseId, Collection<PlatformType> platforms);

    InventoryItemResponse createItem(InventoryItemRequest request);

    InventoryItemResponse getItemById(UUID id);

    PageResponse<InventoryItemResponse> getItems(UUID warehouseId, int page, int size);

    InventoryItemResponse getItemByWarehouseAndVariant(UUID warehouseId, UUID variantId);

    List<InventoryItemResponse> getLowStockItems();

    InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request);

    PageResponse<InventoryTransactionResponse> getTransactions(int page, int size);

    PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID variantId, int page, int size);

    Map<UUID, StockSummaryDTO> getStockSummary(Collection<UUID> variantIds);

    PageResponse<InventoryItemResponse> getInventoryByCategoryId(UUID categoryId, PageRequest pageRequest, int page, int size, UUID channelId, boolean localOnly, String keyword, String status, UUID warehouseId, Collection<PlatformType> platforms);

    void findAllChildIds(UUID parentId, List<UUID> resultList);

    public InventoryDetailDTO getInventoryItemDetail(UUID inventoryItemId);
    public List<AvailableVariantDTO> getAvailableVariantsByWarehouse(UUID warehouseId);
    PageResponse<AvailableVariantDTO> getAvailableVariantsByWarehousePaged(UUID warehouseId, String keyword, int page, int size);
    public InventoryDetailDTO updateInventoryDetail(UUID inventoryItemId, InventoryItemUpdateRequest request);
}
