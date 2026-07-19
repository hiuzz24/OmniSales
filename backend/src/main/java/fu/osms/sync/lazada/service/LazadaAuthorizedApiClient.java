package fu.osms.sync.lazada.service;

import fu.osms.channel.token.service.ChannelTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LazadaAuthorizedApiClient {
    private final LazadaApiClient apiClient;
    private final ChannelTokenService tokenService;

    public String executeGet(UUID channelId, String apiPath, Map<String, String> params) {
        return tokenService.execute(channelId, token -> apiClient.executeGet(
                apiPath, params, token.accessToken(), token.expiresAtEpochSecond()));
    }

    public String executePost(UUID channelId, String apiPath, Map<String, String> params) {
        return tokenService.execute(channelId, token -> apiClient.executePost(
                apiPath, params, token.accessToken(), token.expiresAtEpochSecond()));
    }
}
