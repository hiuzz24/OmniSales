package fu.osms.channel.service;

import fu.osms.channel.service.model.MappingRestoreResult;

import java.util.UUID;

public interface ChannelMappingLifecycleService {

    int archiveForDisconnect(UUID channelId);

    MappingRestoreResult restoreAfterReconnect(UUID channelId);
}
