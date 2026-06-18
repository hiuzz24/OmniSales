package fu.osms.channel.dto.response;

import fu.osms.common.enums.SyncStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSyncResponse {
    private String platform;
    private SyncStatus syncStatus;
    private OffsetDateTime lastSyncedAt;
    private String lastSyncError;
}
