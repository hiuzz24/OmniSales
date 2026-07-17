package fu.osms.sync.tiktok;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;

import java.util.UUID;

public interface TikTokImportSyncService {

    ChannelImportSyncResponse syncProductsAndInventory(UUID channelId);
}
