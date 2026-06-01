package fu.osms.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import fu.osms.audit.entity.AuditLog;
import fu.osms.audit.repository.AuditLogRepository;
import fu.osms.audit.service.AuditService;
import fu.osms.auth.entity.User;
import fu.osms.auth.repository.UserRepository;
import fu.osms.common.dto.PageResponse;
import fu.osms.shop.entity.Shop;
import fu.osms.shop.repository.ShopRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLog> getByShopId(UUID shopId, int page, int size) {
        Page<AuditLog> pageResult = auditLogRepository.findByShopId(shopId, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLog> getByEntity(UUID shopId, String entityType, UUID entityId, int page, int size) {
        Page<AuditLog> pageResult = auditLogRepository
                .findByShopIdAndEntityTypeAndEntityId(shopId, entityType, entityId, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLog> getByActor(UUID shopId, UUID actorId, int page, int size) {
        Page<AuditLog> pageResult = auditLogRepository.findByShopIdAndActorId(shopId, actorId, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLog> getByDateRange(UUID shopId, OffsetDateTime from, OffsetDateTime to, int page, int size) {
        Page<AuditLog> pageResult = auditLogRepository
                .findByShopIdAndPerformedAtBetween(shopId, from, to, PageRequest.of(page, size));
        return toPageResponse(pageResult, page, size);
    }

    @Override
    @Transactional
    public void record(UUID shopId, UUID actorId, String actorEmail, String action,
                       String entityType, UUID entityId, String entityName, Object changes) {
        try {
            Shop shop = shopRepository.getReferenceById(shopId);
            User actor = actorId != null ? userRepository.getReferenceById(actorId) : null;

            @SuppressWarnings("unchecked")
            Map<String, Object> changesMap = changes != null
                    ? objectMapper.convertValue(changes, Map.class)
                    : null;

            AuditLog auditLog = AuditLog.builder()
                    .shop(shop)
                    .actor(actor)
                    .actorEmail(actorEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .changes(changesMap)
                    .performedAt(OffsetDateTime.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage(), e);
        }
    }

    private PageResponse<AuditLog> toPageResponse(Page<AuditLog> p, int page, int size) {
        return PageResponse.<AuditLog>builder()
                .content(p.getContent())
                .page(page).size(size)
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .first(p.isFirst()).last(p.isLast())
                .build();
    }
}
