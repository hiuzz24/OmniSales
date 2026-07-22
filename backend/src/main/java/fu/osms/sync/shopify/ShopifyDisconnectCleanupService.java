package fu.osms.sync.shopify;

import java.util.UUID;

public interface ShopifyDisconnectCleanupService {

    void cleanup(UUID channelId);
}
