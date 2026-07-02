package fu.osms.sync.dto;

import fu.osms.common.enums.PlatformType;
import fu.osms.common.enums.SyncStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLogResponse {
    private UUID id;
    private String jobType;
    private SyncStatus status;
    
    private UUID channelId;
    private String channelName;
    private PlatformType platform;
    
    private UUID productId;
    private String productName;
    private String productSku;
    
    private Integer totalItems;
    private Integer successCount;
    private Integer failCount;
    private String errorSummary;
    
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    
    private String triggeredByEmail;
}
