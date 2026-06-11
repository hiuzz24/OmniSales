package fu.osms.audit.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    public PageResponse<AuditLog> getAll(int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLog> getByEntity(String entityType, UUID entityId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLog> getByActor(UUID actorId, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLog> getByDateRange(OffsetDateTime from, OffsetDateTime to, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }

    @Override
    @Transactional
    public void record(UUID actorId, String actorEmail, String action,
                       String entityType, UUID entityId, String entityName, Object changes) {
        throw new UnsupportedOperationException("Chưa code");
    }

    private PageResponse<AuditLog> toPageResponse(Page<AuditLog> p, int page, int size) {
        throw new UnsupportedOperationException("Chưa code");
    }
}
