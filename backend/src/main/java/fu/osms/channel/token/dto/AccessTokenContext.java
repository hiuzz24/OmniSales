package fu.osms.channel.token.dto;

import fu.osms.common.enums.PlatformType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AccessTokenContext(
        UUID channelId,
        PlatformType platform,
        String accessToken,
        OffsetDateTime tokenExpiresAt
) {
    public Long expiresAtEpochSecond() {
        return tokenExpiresAt == null ? null : tokenExpiresAt.toEpochSecond();
    }
}
