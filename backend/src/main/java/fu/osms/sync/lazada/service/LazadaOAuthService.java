package fu.osms.sync.lazada.service;

import fu.osms.channel.token.dto.PlatformTokenRefreshResult;

import java.util.Map;

public interface LazadaOAuthService {
    String buildAuthorizationUrl();
    Map<String, Object> exchangeToken(String code);
    PlatformTokenRefreshResult refreshToken(String refreshToken);
}
