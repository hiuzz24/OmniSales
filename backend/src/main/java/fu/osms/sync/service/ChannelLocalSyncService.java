package fu.osms.sync.service;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;

import java.util.UUID;

public interface ChannelLocalSyncService {

    ChannelImportSyncResponse syncLocalChanges(UUID channelId);
}
