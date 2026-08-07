package fu.osms.sync.tiktok.inventory;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface TikTokInventoryGateway {

    Map<String, Integer> readAvailable(
            UUID channelId,
            String shopCipher,
            Collection<String> skuIds
    );

    void setAvailable(
            UUID channelId,
            String shopCipher,
            Collection<TikTokInventorySetCommand> commands
    );
}
