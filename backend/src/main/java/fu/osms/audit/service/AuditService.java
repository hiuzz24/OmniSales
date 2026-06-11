package fu.osms.audit.service;

import fu.osms.audit.entity.AuditLog;
import fu.osms.common.dto.PageResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuditService {

    PageResponse<AuditLog> getAll(int page, int size);

    PageResponse<AuditLog> getByEntity(String entityType, UUID entityId, int page, int size);

    PageResponse<AuditLog> getByActor(UUID actorId, int page, int size);

    PageResponse<AuditLog> getByDateRange(OffsetDateTime from, OffsetDateTime to, int page, int size);

    void record(UUID actorId, String actorEmail, String action,
                String entityType, UUID entityId, String entityName, Object changes);
}
