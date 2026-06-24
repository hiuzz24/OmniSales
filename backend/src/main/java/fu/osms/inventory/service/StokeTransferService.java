package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.StockTransferRequest;
import fu.osms.inventory.dto.response.TransferInventoryListResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;

public interface StokeTransferService {
    public TransferInventoryListResponse getTransferListData(String status, UUID warehouseId, String keyword, int page, int size);
    public String generateTransferCode();
    public void createTransferInventory(StockTransferRequest request);
}
