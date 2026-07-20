package fu.osms.sync.tiktok;

import java.util.Collection;
import java.util.UUID;

public interface TikTokInventoryUpdateService {

    int pushAvailableStock(UUID channelId);

    int pushAvailableStock(UUID channelId, Collection<UUID> variantIds);
}
