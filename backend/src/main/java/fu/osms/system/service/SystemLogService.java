package fu.osms.system.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.system.dto.SystemLogResponse;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SystemLogService {
    PageResponse<SystemLogResponse> searchLogs(String level, String keyword, OffsetDateTime from, OffsetDateTime to, int page, int size);
    SystemLogResponse updateLog(UUID id, SystemLogResponse updateDto);
    void deleteLog(UUID id);
}
