package fu.osms.sync.shopify;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.UUID;

public interface ShopifyInventoryUpdateService {

    int syncChangedAvailableStock(UUID channelId,
                                  OffsetDateTime changedSince,
                                  OffsetDateTime changedUntil,
                                  Collection<UUID> productChangedVariantIds);
}
