package fu.osms.sync.service;

import fu.osms.channel.entity.Channel;
import fu.osms.inventory.entity.Warehouse;

public interface MarketplaceWarehouseConsistencyService {

    Warehouse resolveMasterWarehouse();

    Warehouse resolveAndValidatePrimaryWarehouse(Channel channel);

    void validateConnectedPrimaryWarehouses();
}
