package fu.osms.inventory.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

public interface InventoryTransactionService {
    PageResponse<InventoryTransactionDTO> getTransactionsDTOByVariant(UUID variantId, PageRequest pageRequest, int page, int size);
}
