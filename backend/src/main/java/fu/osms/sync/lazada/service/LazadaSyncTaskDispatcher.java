package fu.osms.sync.lazada.service;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;
import fu.osms.sync.lazada.dto.LazadaSyncTask;

public interface LazadaSyncTaskDispatcher {

    ChannelImportSyncResponse dispatch(LazadaSyncTask task);
}
