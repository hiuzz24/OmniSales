package fu.osms.sync.lazada.service;

import fu.osms.channel.dto.response.ChannelImportSyncResponse;

import java.util.UUID;

public interface LazadaImportSyncService {

    ChannelImportSyncResponse syncProductsAndWarehouses(UUID channelId);
}
