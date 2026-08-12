package fu.osms.sync.lazada.service.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.sync.lazada.service.LazadaChannelConnectionService;
import fu.osms.sync.lazada.service.LazadaOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class LazadaChannelConnectionServiceImpl implements LazadaChannelConnectionService {

    private static final int DEFAULT_ACCESS_TOKEN_EXPIRES_IN = 604800;

    private final LazadaOAuthService lazadaOAuthService;
    private final ChannelService channelService;

    @Override
    public String buildAuthorizationUrl() {
        return lazadaOAuthService.buildAuthorizationUrl();
    }

    @Override
    public ChannelResponse connect(String authorizationCode) {
        Map<String, Object> tokenData = lazadaOAuthService.exchangeToken(authorizationCode);
        String accessToken = stringValue(tokenData.get("access_token"));
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("Lazada OAuth callback did not include access_token.");
        }

        return channelService.connectLazada(
                accessToken,
                stringValue(tokenData.get("refresh_token")),
                intValue(tokenData.get("expires_in"), DEFAULT_ACCESS_TOKEN_EXPIRES_IN),
                intValue(tokenData.get("refresh_expires_in"), 0),
                stringValue(tokenData.get("account_id")),
                stringValue(tokenData.get("account_name"))
        );
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
