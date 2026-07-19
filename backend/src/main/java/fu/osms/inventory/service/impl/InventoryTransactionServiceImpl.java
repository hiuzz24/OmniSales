package fu.osms.inventory.service.impl;

import fu.osms.common.dto.PageResponse;
import fu.osms.inventory.dto.response.InventoryTransactionDTO;
import fu.osms.inventory.dto.response.InventoryTransactionResponse;
import fu.osms.inventory.entity.InventoryTransaction;
import fu.osms.inventory.enums.InvTxnType;
import fu.osms.inventory.mapper.InventoryTransactionDTOMapper;
import fu.osms.inventory.mapper.InventoryTransactionMapper;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.inventory.repository.InventoryTransactionSpecification;
import fu.osms.inventory.service.InventoryTransactionService;
//import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryTransactionServiceImpl implements InventoryTransactionService {

    private final InventoryTransactionRepository inventoryTransactionRepository;

    private final InventoryTransactionDTOMapper inventoryTransactionDTOMapper;

    private final InventoryTransactionMapper   inventoryTransactionMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDTO> getTransactionsDTO(PageRequest pageRequest, int page, int size) {
        Page<InventoryTransaction> transactionPage = inventoryTransactionRepository.findAllWithDetails(pageRequest);

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDTO> getTransactionsDTOByVariant(UUID variantId, PageRequest pageRequest, int page, int size) {
        Page<InventoryTransaction> transactionPage = inventoryTransactionRepository.findByVariantIdWithDetails(variantId, pageRequest);

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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDTO> getTransactionsDTOByProduct(UUID productId, PageRequest pageRequest) {
        Page<InventoryTransaction> transactionPage =
                inventoryTransactionRepository.findByProductIdWithDetails(productId, pageRequest);
        List<InventoryTransactionDTO> content = transactionPage.getContent().stream()
                .map(inventoryTransactionDTOMapper::toDto)
                .toList();

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

    @Override
    public Page<InventoryTransactionResponse> getInventoryLogs(UUID warehouseId, String productSearch, InvTxnType type, OffsetDateTime startDate, OffsetDateTime endDate, UUID performedById, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt"));

        Specification<InventoryTransaction> spec = InventoryTransactionSpecification.filterLogs(
                warehouseId, productSearch, type, startDate, endDate, performedById
        );

        Page<InventoryTransaction> transactions = inventoryTransactionRepository.findAll(spec, pageable);

        return transactions.map(inventoryTransactionMapper::toResponse);
    }
}
