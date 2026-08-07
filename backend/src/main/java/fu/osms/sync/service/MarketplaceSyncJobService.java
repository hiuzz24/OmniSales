package fu.osms.sync.service;

import fu.osms.sync.dto.MarketplaceSyncJobResponse;

import java.util.UUID;

public interface MarketplaceSyncJobService {
    MarketplaceSyncJobResponse enqueueRemoteSync(UUID channelId);

    MarketplaceSyncJobResponse getJob(UUID jobId);
}
