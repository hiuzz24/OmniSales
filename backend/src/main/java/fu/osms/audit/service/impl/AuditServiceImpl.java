package fu.osms.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.audit.service.AuditService;
import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getAll(int page, int size) {
        Page<AuditLog> result = auditLogRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt")));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByEntity(String entityType, UUID entityId, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findByEntityTypeAndEntityId(entityType, entityId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt")));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByActor(UUID actorId, int page, int size) {
        Page<AuditLog> result = auditLogRepository.findByActorId(actorId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt")));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByDateRange(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findByPerformedAtBetween(from, to, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt")));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByEntityType(String entityType, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findByEntityType(entityType, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt")));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByAction(String action, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findByAction(action, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt")));
        return toPageResponse(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByEntityTypeAndAction(String entityType, String action, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findByEntityTypeAndAction(entityType, action, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "performedAt")));
        return toPageResponse(result);
    }

    public PageResponse<AuditLogResponse> searchOrderLogs(String keyword, OffsetDateTime from, OffsetDateTime to, String action, int page, int size) {
        Page<AuditLog> result = auditLogRepository.searchOrderLogs(
                keyword,
                from,
                to,
                action,
                PageRequest.of(page, size, Sort.unsorted()));
        return toPageResponse(result);
    }

    @Override
    @Transactional
    public void record(UUID actorId, String actorEmail, String action,
                       String entityType, UUID entityId, String entityName, Object changes) {
        AuditLog log = AuditLog.builder()
                .actor(userRepository.findById(actorId).orElse(null))
                .actorEmail(actorEmail)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .entityName(entityName)
                .performedAt(OffsetDateTime.now())
                .build();
        if (changes != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> changesMap = objectMapper.convertValue(changes, Map.class);
            log.setChanges(changesMap);
        }
        auditLogRepository.save(log);
    }

    private PageResponse<AuditLogResponse> toPageResponse(Page<AuditLog> p) {
        return PageResponse.<AuditLogResponse>builder()
                .content(p.getContent().stream().map(this::toDto).toList())
                .page(p.getNumber())
                .size(p.getSize())
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .first(p.isFirst())
                .last(p.isLast())
                .build();
    }

    private AuditLogResponse toDto(AuditLog entity) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .actorId(entity.getActor() != null ? entity.getActor().getId() : null)
                .actorEmail(entity.getActorEmail())
                .action(entity.getAction())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .entityName(entity.getEntityName())
                .changes(entity.getChanges())
                .performedAt(entity.getPerformedAt())
                .build();
    }
}
