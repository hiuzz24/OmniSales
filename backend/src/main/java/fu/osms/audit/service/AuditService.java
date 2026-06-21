package fu.osms.audit.service;

import fu.osms.audit.dto.response.AuditLogResponse;
import fu.osms.audit.entity.AuditLog;
import fu.osms.common.dto.PageResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuditService {

    PageResponse<AuditLogResponse> getAll(int page, int size);

    PageResponse<AuditLogResponse> getByEntity(String entityType, UUID entityId, int page, int size);

    PageResponse<AuditLogResponse> getByActor(UUID actorId, int page, int size);

    PageResponse<AuditLogResponse> getByDateRange(OffsetDateTime from, OffsetDateTime to, int page, int size);

    PageResponse<AuditLogResponse> getByEntityType(String entityType, int page, int size);

    PageResponse<AuditLogResponse> getByAction(String action, int page, int size);

    PageResponse<AuditLogResponse> getByEntityTypeAndAction(String entityType, String action, int page, int size);

    PageResponse<AuditLogResponse> searchOrderLogs(String keyword, OffsetDateTime from, OffsetDateTime to, String action, int page, int size);

    void record(UUID actorId, String actorEmail, String action,
                String entityType, UUID entityId, String entityName, Object changes);
}
