package fu.osms.sync.service.impl;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.sync.service.ChannelRemoteSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketplaceSyncJobWorker {

    private final ChannelRemoteSyncService channelRemoteSyncService;
    private final SyncJobProgressTracker progressTracker;

    @Async("syncJobExecutor")
    public void executeRemoteSync(UUID jobId, UUID channelId) {
        progressTracker.bind(jobId);
        try {
            ChannelImportSyncResponse result = channelRemoteSyncService.syncRemoteChanges(channelId);
            progressTracker.complete(jobId, result.getVariantCount());
        } catch (Exception exception) {
            log.error("[MarketplaceSyncJob] Remote sync failed jobId={}, channelId={}", jobId, channelId, exception);
            progressTracker.fail(jobId, exception.getMessage());
        } finally {
            progressTracker.clear();
        }
    }
}
