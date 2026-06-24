package fu.osms.sync.lazada.service;

import java.util.Map;

public interface LazadaApiClient {
    String executePost(String apiPath, Map<String, String> businessParams, String accessToken, Long tokenExpiresAt);
}
