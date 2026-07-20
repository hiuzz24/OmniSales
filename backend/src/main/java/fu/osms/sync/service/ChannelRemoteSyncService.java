package fu.osms.sync.service;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;

import java.util.UUID;

public interface ChannelRemoteSyncService {

    ChannelImportSyncResponse syncRemoteChanges(UUID channelId);

    ChannelImportSyncResponse syncAllRemoteChanges(UUID requestedChannelId);
}
