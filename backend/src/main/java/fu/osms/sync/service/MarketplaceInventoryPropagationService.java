package fu.osms.sync.service;

import java.util.Collection;
import java.util.UUID;

public interface MarketplaceInventoryPropagationService {

    void schedulePushAvailableStock(Collection<UUID> variantIds);

    void pushAvailableStock(Collection<UUID> variantIds);
}
