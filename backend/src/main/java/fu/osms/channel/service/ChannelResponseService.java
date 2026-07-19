package fu.osms.channel.service;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.entity.Channel;

public interface ChannelResponseService {
    void enrichStats(Channel channel);

    ChannelResponse toResponse(Channel channel);
}
