package fu.osms.sync.service;

import fu.osms.channel.entity.Channel;
import fu.osms.inventory.entity.Warehouse;
import fu.osms.inventory.dto.response.WarehouseAddressComparisonResult;

import java.util.List;

public interface MarketplaceWarehouseConsistencyService {

    Warehouse resolveMasterWarehouse();

    Warehouse resolveAndValidatePrimaryWarehouse(Channel channel);

    void validateConnectedPrimaryWarehouses();

    /**
     * Fetch the primary/default warehouse address from each connected marketplace channel.
     * Returns one entry per connected channel with its platform, channel name, and address.
     */
    List<WarehouseAddressComparisonResult.PlatformAddress> fetchConnectedPlatformAddresses();
}
