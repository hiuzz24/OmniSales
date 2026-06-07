package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.InventoryItemRequest;
import fu.osms.inventory.dto.request.InventoryTransactionRequest;
import fu.osms.inventory.dto.response.InventoryItemResponse;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.common.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface InventoryService {

    InventoryItemResponse createItem(InventoryItemRequest request);

    InventoryItemResponse getItemById(UUID id);

    PageResponse<InventoryItemResponse> getItemsByShop(UUID shopId, int page, int size);

    List<InventoryItemResponse> getLowStockItems(UUID shopId);

    InventoryTransactionResponse recordTransaction(InventoryTransactionRequest request);

    PageResponse<InventoryTransactionResponse> getTransactionsByShop(UUID shopId, int page, int size);

    PageResponse<InventoryTransactionResponse> getTransactionsByVariant(UUID shopId, UUID variantId, int page, int size);
}
