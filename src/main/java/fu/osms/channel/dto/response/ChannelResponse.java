package fu.osms.channel.dto.response;

import fu.osms.common.enums.PlatformType;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelResponse {

    private UUID id;
    private UUID shopId;
    private String shopName;
    private PlatformType platform;
    private String displayName;
    private String status;
    private String region;
    private Map<String, Object> metadata;
    private OffsetDateTime lastSyncedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
