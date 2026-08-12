package fu.osms.channel.controller;

import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.lazada.service.LazadaChannelConnectionService;
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

    private final LazadaChannelConnectionService lazadaChannelConnectionService;
    private final ChannelConnectionLogService channelConnectionLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> authorize() {
        try {
            String authUrl = lazadaChannelConnectionService.buildAuthorizationUrl();
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
            lazadaChannelConnectionService.connect(code);
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

}
