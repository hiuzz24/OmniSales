package fu.osms.sync.lazada.service;

import fu.osms.sync.lazada.dto.LazadaInventorySyncResult;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface LazadaInventoryUpdateService {

    LazadaInventorySyncResult syncChangedSellableStock(UUID channelId,
                                                       OffsetDateTime changedSince,
                                                       Collection<UUID> productChangedVariantIds);
}
