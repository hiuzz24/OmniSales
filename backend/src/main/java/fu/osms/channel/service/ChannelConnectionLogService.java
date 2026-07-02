package fu.osms.channel.service;

import fu.osms.channel.dto.response.ChannelConnectionLogResponse;
import fu.osms.channel.entity.Channel;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.enums.ChannelConnectionLogStatus;
import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;

import java.util.Map;
import java.util.UUID;

public interface ChannelConnectionLogService {
    PageResponse<ChannelConnectionLogResponse> search(
            PlatformType platform,
            ChannelConnectionLogStatus status,
            ChannelConnectionAction action,
            UUID channelId,
            int page,
            int size
    );

    void logSuccess(Channel channel, ChannelConnectionAction action, String message, Map<String, Object> metadata);

    void logFailure(PlatformType platform, ChannelConnectionAction action, String message, String errorMessage, Map<String, Object> metadata);
}
