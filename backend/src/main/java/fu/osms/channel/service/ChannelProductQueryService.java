package fu.osms.channel.service;

import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelSyncResponse;
import fu.osms.common.dto.PageResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChannelProductQueryService {
    PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size);

    Map<UUID, List<String>> getProductChannels(Collection<UUID> productIds);

    Map<UUID, List<UUID>> getProductChannelIds(Collection<UUID> productIds);

    Map<UUID, List<ChannelSyncResponse>> getProductChannelSyncs(Collection<UUID> productIds);
}
