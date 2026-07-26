package fu.osms.sync.shopify.inventory;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface ShopifyInventoryGateway {

    Map<InventoryLocationKey, Integer> readAvailable(
            UUID channelId,
            Collection<InventoryLocationKey> keys
    );

    int setAvailable(
            UUID channelId,
            Collection<ShopifyInventorySetCommand> commands,
            String idempotencyKey
    );

    String resolveManagedLocationId(UUID channelId);
}
