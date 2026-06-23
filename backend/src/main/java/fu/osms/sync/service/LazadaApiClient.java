package fu.osms.sync.service;

import java.util.Map;

public interface LazadaApiClient {
    /**
     * Executes a POST request to the Lazada API.
     *
     * @param apiPath         The API path (e.g. "/product/create")
     * @param businessParams  The business parameters for the request
     * @param accessToken     The access token
     * @param tokenExpiresAt  The expiration time of the token (Unix timestamp in seconds), can be null if not tracked
     * @return The JSON response string
     */
    String executePost(String apiPath, Map<String, String> businessParams, String accessToken, Long tokenExpiresAt);
}
