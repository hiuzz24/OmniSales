package fu.osms.sync.lazada.service;

import java.util.Map;

public interface LazadaOAuthService {
    String buildAuthorizationUrl();
    Map<String, Object> exchangeToken(String code);
}
