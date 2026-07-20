package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.request.StockReceiveRequest;
import fu.osms.inventory.dto.response.StockReceiveResponse;

import java.util.UUID;

public interface StockReceiveService {
    StockReceiveResponse createReceipt(StockReceiveRequest request, UUID createdByUserId);
    PageResponse<StockReceiveResponse> getReceipts(int page, int size);
    StockReceiveResponse getReceiptById(UUID id);
    StockReceiveResponse updateReceipt(UUID receiptId, StockReceiveRequest request, UUID updatedByUserId);
    StockReceiveResponse completeReceipt(UUID receiptId, UUID approvedByUserId);
    Object getReceiptStatistics();
    String getNextReceiptCode();
    int syncPendingMarketplaceInventory();
}
