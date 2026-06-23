package fu.osms.sync.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.SyncStatus;
import fu.osms.sync.dto.SyncLogResponse;

import java.util.UUID;

public interface SyncLogService {
    PageResponse<SyncLogResponse> search(SyncStatus status, UUID channelId, int page, int size);
}
