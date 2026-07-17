package fu.osms.catalog.service;

import fu.osms.catalog.dto.request.ChannelConfigRequest;
import fu.osms.catalog.dto.response.ChannelProductConfigResponse;
import fu.osms.channel.entity.ChannelProduct;

import java.util.UUID;

public interface ProductChannelConfigService {
    ChannelProductConfigResponse getConfig(UUID productId, UUID channelId);

    ChannelProductConfigResponse updateConfig(UUID productId, UUID channelId, ChannelConfigRequest request);

    void applyInitialConfig(ChannelProduct channelProduct, ChannelConfigRequest request);

    boolean isReady(ChannelProduct channelProduct);

    String configurationError(ChannelProduct channelProduct);
}
