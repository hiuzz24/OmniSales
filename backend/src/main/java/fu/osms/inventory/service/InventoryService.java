package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import fu.osms.inventory.dto.response.StockSummaryDTO;

public interface InventoryService {

    InventoryItemResponse createItem(InventoryItemRequest request);

    InventoryItemResponse getItemById(UUID id);

    PageResponse<InventoryItemResponse> getItems(UUID warehouseId, int page, int size);

    InventoryItemResponse getItemByWarehouseAndVariant(UUID warehouseId, UUID variantId);

    List<InventoryItemResponse> getLowStockItems();

    InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request);

    PageResponse<InventoryTransactionResponse> getTransactions(int page, int size);

    PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID variantId, int page, int size);

    Map<UUID, StockSummaryDTO> getStockSummary(Collection<UUID> variantIds);
}
