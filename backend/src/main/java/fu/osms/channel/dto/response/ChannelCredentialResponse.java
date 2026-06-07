package fu.osms.channel.dto.response;

import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelCredentialResponse {

    private UUID id;
    private UUID channelId;
    private String channelName;
    private OffsetDateTime tokenExpiresAt;
    private String connectionState;
    private OffsetDateTime lastRefreshedAt;
    private String refreshError;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
