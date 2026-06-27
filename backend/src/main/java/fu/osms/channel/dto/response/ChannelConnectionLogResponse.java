package fu.osms.channel.dto.response;

import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.enums.ChannelConnectionLogStatus;
import fu.osms.common.enums.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelConnectionLogResponse {
    private UUID id;
    private PlatformType platform;
    private UUID channelId;
    private String channelName;
    private String entityType;
    private ChannelConnectionAction action;
    private ChannelConnectionLogStatus status;
    private String message;
    private String errorMessage;
    private Map<String, Object> metadata;
    private OffsetDateTime createdAt;
}
