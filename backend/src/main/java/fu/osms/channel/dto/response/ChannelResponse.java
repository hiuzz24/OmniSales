package fu.osms.channel.dto.response;

import fu.osms.common.enums.PlatformType;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelResponse {

    private UUID id;

    private PlatformType platform;
    private String displayName;
    private String status;
    private String connectionState;
    private OffsetDateTime tokenExpiresAt;
    private OffsetDateTime refreshTokenExpiresAt;
    private String refreshError;
    private String region;
    private Map<String, Object> metadata;
    private Boolean syncEnabled;
    private BigDecimal commissionRate;
    private OffsetDateTime lastSyncedAt;
    private OffsetDateTime lastSyncedApplicationAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
