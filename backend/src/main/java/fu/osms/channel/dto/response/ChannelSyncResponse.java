package fu.osms.channel.dto.response;

import fu.osms.common.enums.SyncStatus;
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
public class ChannelSyncResponse {
    private UUID channelId;
    private String channelName;
    private String platform;
    private SyncStatus syncStatus;
    private OffsetDateTime lastSyncedAt;
    private String lastSyncError;
    private Boolean readyToSync;
    private String configurationError;
    private Map<String, Object> platformConfig;
}
