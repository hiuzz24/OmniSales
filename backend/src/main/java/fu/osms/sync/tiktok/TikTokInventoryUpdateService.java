package fu.osms.sync.tiktok;

import java.util.UUID;

public interface TikTokInventoryUpdateService {

    int pushAvailableStock(UUID channelId);
}
