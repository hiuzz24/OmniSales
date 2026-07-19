package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.enums.InvTxnType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface InventoryTransactionService {
    PageResponse<InventoryTransactionDTO> getTransactionsDTO(PageRequest pageRequest, int page, int size);
    PageResponse<InventoryTransactionDTO> getTransactionsDTOByVariant(UUID variantId, PageRequest pageRequest, int page, int size);
    PageResponse<InventoryTransactionDTO> getTransactionsDTOByProduct(UUID productId, PageRequest pageRequest);

    public Page<InventoryTransactionResponse> getInventoryLogs(
            UUID warehouseId,
            String productSearch,
            InvTxnType type,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            UUID performedById,
            int page,
            int size);
}
