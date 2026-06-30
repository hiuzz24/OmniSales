package fu.osms.sync.dto;

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
public class WebhookEventResponse {
    private UUID id;
    private UUID channelId;
    private String channelName;
    private PlatformType platform;
    private String eventType;
    private String externalEventId;
    private String status;
    private Map<String, Object> rawPayload;
    private String errorMessage;
    private OffsetDateTime receivedAt;
    private OffsetDateTime processedAt;
}
