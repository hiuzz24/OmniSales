package fu.osms.channel.token.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class PlatformTokenRefreshResult {
    private final String accessToken;
    private final String refreshToken;
    private final OffsetDateTime tokenExpiresAt;
    private final OffsetDateTime refreshTokenExpiresAt;
    private final List<String> grantedScopes;
    private final String accountId;
}
