package fu.osms.sync.dto;

import fu.osms.common.enums.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceSyncJobResponse {
    private UUID jobId;
    private UUID channelId;
    private String channelName;
    private PlatformType platform;
    private String status;
    private int totalItems;
    private int processedItems;
    private int successCount;
    private int failCount;
    private int progressPercent;
    private String message;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
