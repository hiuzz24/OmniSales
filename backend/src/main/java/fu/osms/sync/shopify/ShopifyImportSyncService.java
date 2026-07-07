package fu.osms.sync.shopify;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;

import java.util.UUID;

public interface ShopifyImportSyncService {

    ChannelImportSyncResponse syncProductsAndInventory(UUID channelId);
}
