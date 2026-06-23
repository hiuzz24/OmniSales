package fu.osms.sync.service;

import java.util.Map;

public interface LazadaOAuthService {
    String buildAuthorizationUrl();
    Map<String, Object> exchangeToken(String code);
}
