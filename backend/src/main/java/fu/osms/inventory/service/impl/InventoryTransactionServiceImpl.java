package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.mapper.InventoryTransactionDTOMapper;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.service.InventoryTransactionService;
//import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InventoryTransactionServiceImpl implements InventoryTransactionService {
    @Autowired
    private InventoryTransactionRepository inventoryTransactionRepository;
    @Autowired
    private InventoryTransactionDTOMapper inventoryTransactionDTOMapper;


    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDTO> getTransactionsDTOByVariant(UUID variantId, PageRequest pageRequest, int page, int size) {
        Page<InventoryTransaction> transactionPage = inventoryTransactionRepository.findByVariantId(variantId, pageRequest);

        List<InventoryTransactionDTO> content = transactionPage.getContent().stream()
                .map(inventoryTransactionDTOMapper::toDto)
                .collect(Collectors.toList());

        return PageResponse.<InventoryTransactionDTO>builder()
                .content(content)
                .page(transactionPage.getNumber())
                .size(transactionPage.getSize())
                .totalElements(transactionPage.getTotalElements())
                .totalPages(transactionPage.getTotalPages())
                .first(transactionPage.isFirst())
                .last(transactionPage.isLast())
                .build();
    }
}
