package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryItemResponse createItem(InventoryItemRequest request);

    InventoryItemResponse getItemById(UUID id);

    PageResponse<InventoryItemResponse> getItems(UUID warehouseId, int page, int size);

    List<InventoryItemResponse> getLowStockItems();

    InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request);

    PageResponse<InventoryTransactionResponse> getTransactions(int page, int size);

    PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID variantId, int page, int size);
}
