package fu.osms.channel.controller;

import fu.osms.channel.service.ChannelService;
import fu.osms.sync.service.LazadaOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channels/lazada")
@RequiredArgsConstructor
public class LazadaOAuthController {

    private final LazadaOAuthService lazadaOAuthService;
    private final ChannelService channelService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize() {
        String authUrl = lazadaOAuthService.buildAuthorizationUrl();
        log.info("[LazadaOAuthController] Redirecting to {}", authUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authUrl))
                .build();
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletResponse response
    ) throws Exception {
        log.info("[LazadaOAuthController] Callback code={}, error={}", code, error);

        if (error != null) {
            log.error("[LazadaOAuthController] Lazada returned error: {}", error);
            response.sendRedirect(frontendUrl + "/channels?error=" + error);
            return;
        }

        if (code == null || code.isBlank()) {
            log.error("[LazadaOAuthController] Missing code in callback");
            response.sendRedirect(frontendUrl + "/channels?error=missing_code");
            return;
        }

        try {
            Map<String, Object> tokenData = lazadaOAuthService.exchangeToken(code);
            String accessToken = (String) tokenData.get("access_token");
            String refreshToken = (String) tokenData.get("refresh_token");
            Integer expiresIn = (Integer) tokenData.get("expires_in");
            String accountId = (String) tokenData.get("account_id");
            String accountPlatform = (String) tokenData.get("account");

            channelService.connectLazada(accessToken, refreshToken, expiresIn != null ? expiresIn : 604800, accountId, accountPlatform);

            response.sendRedirect(frontendUrl + "/channels?success=lazada_connected");
        } catch (Exception e) {
            log.error("[LazadaOAuthController] Failed to exchange token and connect channel", e);
            response.sendRedirect(frontendUrl + "/channels?error=connection_failed");
        }
    }
}
