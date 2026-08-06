package fu.osms.inventory.service;

import fu.osms.inventory.dto.request.WarehouseMarketplaceSyncRequest;
import fu.osms.inventory.dto.response.WarehouseMarketplaceSyncResult;

import java.util.UUID;

public interface WarehouseSyncService {

    /**
     * Sync warehouse name, address, contact info to all connected marketplace channels
     * (Shopify, Lazada, TikTok). Also updates the internal Warehouse entity.
     * Partial failures are tolerated — each channel result is returned individually.
     */
    WarehouseMarketplaceSyncResult syncToMarketplaces(UUID warehouseId, WarehouseMarketplaceSyncRequest request);
}
