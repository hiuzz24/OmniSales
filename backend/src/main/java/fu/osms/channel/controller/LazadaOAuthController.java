package fu.osms.channel.controller;

import fu.osms.channel.service.ChannelService;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.lazada.service.LazadaOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channels/lazada")
@RequiredArgsConstructor
public class LazadaOAuthController {

    private final LazadaOAuthService lazadaOAuthService;
    private final ChannelService channelService;
    private final ChannelConnectionLogService channelConnectionLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> authorize() {
        try {
            String authUrl = lazadaOAuthService.buildAuthorizationUrl();
            log.info("[LazadaOAuthController] Returning authorization URL");
            return ResponseEntity.ok(ApiResponse.success(Map.of("url", authUrl)));
        } catch (IllegalStateException e) {
            log.error("[LazadaOAuthController] Invalid Lazada OAuth configuration: {}", e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
        }
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            HttpServletResponse response) throws IOException {
        if (error != null) {
            log.error("[LazadaOAuthController] Lazada returned error: {}", error);
            channelConnectionLogService.logFailure(
                    PlatformType.LAZADA,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect Lazada channel",
                    error,
                    Map.of("oauthError", error)
            );
            response.sendRedirect(frontendUrl + "/channels?error=" + error);
            return;
        }

        if (code == null || code.isBlank()) {
            log.error("[LazadaOAuthController] Missing code in callback");
            channelConnectionLogService.logFailure(
                    PlatformType.LAZADA,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect Lazada channel",
                    "Missing code in callback",
                    Map.of("reason", "missing_code")
            );
            response.sendRedirect(frontendUrl + "/channels?error=missing_code");
            return;
        }

        try {
            Map<String, Object> tokenData = lazadaOAuthService.exchangeToken(code);
            String accessToken = toStringValue(tokenData.get("access_token"));
            String refreshToken = toStringValue(tokenData.get("refresh_token"));
            int expiresIn = toIntValue(tokenData.get("expires_in"), 604800);
            int refreshExpiresIn = toIntValue(tokenData.get("refresh_expires_in"), 0);
            String accountId = toStringValue(tokenData.get("account_id"));
            String accountName = toStringValue(tokenData.get("account_name"));

            if (accessToken == null || accessToken.isBlank()) {
                throw new IllegalStateException("Lazada OAuth callback không có access_token.");
            }

            channelService.connectLazada(accessToken, refreshToken, expiresIn,
                    refreshExpiresIn, accountId, accountName);

            response.sendRedirect(frontendUrl + "/channels?success=lazada_connected");
        } catch (AppException e) {
            log.error("[LazadaOAuthController] Failed to connect channel", e);
            channelConnectionLogService.logFailure(
                    PlatformType.LAZADA, ChannelConnectionAction.CONNECT,
                    "Failed to connect Lazada channel", e.getMessage(), Map.of("reason", "connection_failed"));
            String errorCode = e.getErrorCode() == ErrorCode.CHANNEL_IDENTITY_CONFLICT
                    ? "channel_identity_conflict" : "connection_failed";
            response.sendRedirect(frontendUrl + "/channels?error=" + errorCode);
        } catch (Exception e) {
            log.error("[LazadaOAuthController] Failed to exchange token and connect channel", e);
            channelConnectionLogService.logFailure(
                    PlatformType.LAZADA,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect Lazada channel",
                    e.getMessage(),
                    Map.of("reason", "connection_failed")
            );
            response.sendRedirect(frontendUrl + "/channels?error=connection_failed");
        }
    }

    private String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int toIntValue(Object value, int fallback) {
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
