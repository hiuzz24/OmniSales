package fu.osms.channel.controller;

import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.tiktok.TikTokChannelConnectionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channels/tiktok")
@RequiredArgsConstructor
public class TikTokOAuthController {

    private final TikTokChannelConnectionService tikTokChannelConnectionService;
    private final ChannelConnectionLogService channelConnectionLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/callback")
    /** Hoàn tất ủy quyền TikTok và chuyển người bán về màn hình kênh. */
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String state,
            HttpServletResponse response) throws IOException {

        if (error != null) {
            log.error("[TikTokOAuth] TikTok returned error: {}", error);
            channelConnectionLogService.logFailure(
                    PlatformType.TIKTOK,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect TikTok channel",
                    error,
                    Map.of("oauthError", error)
            );
            response.sendRedirect(frontendUrl + "/channels?error=tiktok_oauth_failed");
            return;
        }

        if (code == null || code.isBlank()) {
            log.error("[TikTokOAuth] Missing code in callback");
            channelConnectionLogService.logFailure(
                    PlatformType.TIKTOK,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect TikTok channel",
                    "Missing code in callback",
                    Map.of("reason", "missing_code")
            );
            response.sendRedirect(frontendUrl + "/channels?error=tiktok_oauth_failed");
            return;
        }

        try {
            tikTokChannelConnectionService.connect(code, state);
            response.sendRedirect(frontendUrl + "/channels?success=tiktok_connected");
        } catch (AppException e) {
            log.error("[TikTokOAuth] Failed to connect channel", e);
            channelConnectionLogService.logFailure(
                    PlatformType.TIKTOK, ChannelConnectionAction.CONNECT,
                    "Failed to connect TikTok channel", e.getMessage(), Map.of("reason", "connection_failed"));
            String errorCode = e.getErrorCode() == ErrorCode.CHANNEL_IDENTITY_CONFLICT
                    ? "channel_identity_conflict" : "tiktok_oauth_failed";
            response.sendRedirect(frontendUrl + "/channels?error=" + errorCode);
        } catch (Exception e) {
            log.error("[TikTokOAuth] Failed to exchange token and connect channel", e);
            channelConnectionLogService.logFailure(
                    PlatformType.TIKTOK,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect TikTok channel",
                    e.getMessage(),
                    Map.of("reason", "connection_failed")
            );
            response.sendRedirect(frontendUrl + "/channels?error=tiktok_oauth_failed");
        }
    }
}
