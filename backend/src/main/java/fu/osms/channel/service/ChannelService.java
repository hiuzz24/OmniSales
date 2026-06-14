package fu.osms.channel.service;

import fu.osms.channel.dto.request.ChannelRequest;
import fu.osms.channel.dto.response.ChannelCredentialResponse;
import fu.osms.channel.dto.response.ChannelProductResponse;
import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.common.dto.PageResponse;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChannelService {

    ChannelResponse create(ChannelRequest request);

    ChannelResponse getById(UUID id);

    List<ChannelResponse> getAll();

    ChannelResponse update(UUID id, ChannelRequest request);

    void delete(UUID id);

    ChannelCredentialResponse getCredential(UUID channelId);

    PageResponse<ChannelProductResponse> getChannelProducts(UUID channelId, int page, int size);

    Map<UUID, List<String>> getProductChannels(Collection<UUID> productIds);
}
