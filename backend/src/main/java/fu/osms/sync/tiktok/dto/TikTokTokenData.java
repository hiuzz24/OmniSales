package fu.osms.sync.tiktok.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class TikTokTokenData {

    private final String accessToken;
    private final String refreshToken;
    private final int expiresInSeconds;
    private final String accountId;
    private final String accountName;
    private final Map<String, Object> metadata;
}
