package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.WarehouseMarketplaceSyncRequest;
import fu.osms.inventory.dto.response.WarehouseAddressComparisonResult;
import fu.osms.inventory.dto.response.WarehouseMarketplaceSyncResult;

import java.util.UUID;

public interface WarehouseSyncService {

    /**
     * Sync warehouse name, address, contact info to all connected marketplace channels
     * (Shopify, Lazada, TikTok). Also updates the internal Warehouse entity.
     * Partial failures are tolerated — each channel result is returned individually.
     */
    WarehouseMarketplaceSyncResult syncToMarketplaces(UUID warehouseId, WarehouseMarketplaceSyncRequest request);

    /**
     * Compare default warehouse addresses from all connected marketplace platforms
     * with the current master warehouse address.
     */
    WarehouseAddressComparisonResult comparePlatformAddresses();

    /**
     * Apply warehouse address sync: create a new warehouse with the synced address
     * and deactivate the old one. Channel metadata is updated to point to the new warehouse.
     * Old documents (receipts, transfers, etc.) keep their reference to the old warehouse.
     *
     * @param confirm must be true to actually apply the change
     */
    void applyAddressSync(boolean confirm);
}
