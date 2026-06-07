package fu.osms.channel.dto.response;

import fu.osms.common.enums.SyncStatus;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelProductResponse {

    private UUID id;
    private UUID channelId;
    private String channelName;
    private UUID productId;
    private String productName;
    private String externalProductId;
    private String externalStatus;
    private String mappingState;
    private SyncStatus syncStatus;
    private OffsetDateTime lastSyncedAt;
    private String lastSyncError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
