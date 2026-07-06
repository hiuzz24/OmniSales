package fu.osms.sync.service.impl;

import fu.osms.catalog.entity.Product;
import fu.osms.channel.entity.Channel;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.SyncStatus;
import fu.osms.inventory.mapper.InventoryTransactionDTOMapper;
import fu.osms.inventory.repository.InventoryTransactionRepository;
import fu.osms.sync.dto.SyncLogResponse;
import fu.osms.sync.entity.SyncLog;
import fu.osms.sync.mapper.SyncLogMapper;
import fu.osms.sync.repository.SyncLogRepository;
import fu.osms.sync.service.SyncLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class SyncLogServiceImpl implements SyncLogService {

    private final SyncLogRepository syncLogRepository;
    private final SyncLogMapper syncLogMapper;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final InventoryTransactionDTOMapper inventoryTransactionDTOMapper;

    @Override
    public PageResponse<SyncLogResponse> search(SyncStatus status, UUID channelId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt"));

        Specification<SyncLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (channelId != null) {
                predicates.add(cb.equal(root.get("channel").get("id"), channelId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SyncLog> syncLogPage = syncLogRepository.findAll(spec, pageable);

        List<SyncLogResponse> content = syncLogPage.getContent().stream()
                .map(this::toResponseWithInventoryChanges)
                .collect(Collectors.toList());

        return PageResponse.<SyncLogResponse>builder()
                .content(content)
                .page(syncLogPage.getNumber())
                .size(syncLogPage.getSize())
                .totalElements(syncLogPage.getTotalElements())
                .totalPages(syncLogPage.getTotalPages())
                .first(syncLogPage.isFirst())
                .last(syncLogPage.isLast())
                .build();
    }

    private SyncLogResponse toResponseWithInventoryChanges(SyncLog syncLog) {
        SyncLogResponse response = syncLogMapper.toResponse(syncLog);
        response.setInventoryChanges(
                inventoryTransactionRepository
                        .findByReferenceTypeAndReferenceIdWithDetails("SYNC", syncLog.getId())
                        .stream()
                        .map(inventoryTransactionDTOMapper::toDto)
                        .toList()
        );
        return response;
    }
}
