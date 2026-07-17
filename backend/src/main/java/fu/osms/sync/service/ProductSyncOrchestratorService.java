package fu.osms.sync.service;

import fu.osms.sync.dto.SyncResult;
import java.util.UUID;

public interface ProductSyncOrchestratorService {
    SyncResult syncProductToAllChannels(UUID productId);

    SyncResult syncProductToChannel(UUID productId, UUID channelId);
}
