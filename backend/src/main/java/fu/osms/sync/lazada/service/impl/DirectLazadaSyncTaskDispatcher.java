package fu.osms.sync.lazada.service.impl;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.sync.lazada.dto.LazadaSyncTask;
import fu.osms.sync.lazada.service.LazadaChannelSyncService;
import fu.osms.sync.lazada.service.LazadaSyncTaskDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DirectLazadaSyncTaskDispatcher implements LazadaSyncTaskDispatcher {

    private final LazadaChannelSyncService lazadaChannelSyncService;

    @Override
    public ChannelImportSyncResponse dispatch(LazadaSyncTask task) {
        return lazadaChannelSyncService.syncLocalChanges(task);
    }
}
