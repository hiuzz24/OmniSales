package fu.osms.sync.lazada.inventory;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface LazadaInventoryGateway {

    Map<LazadaInventoryKey, Integer> readSellable(
            UUID channelId,
            Collection<LazadaInventoryKey> keys
    );

    int updateSellable(
            UUID channelId,
            Collection<LazadaInventorySetCommand> commands
    );
}
