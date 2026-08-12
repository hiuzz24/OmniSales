package fu.osms.channel.controller;

import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.shopify.ShopifyChannelConnectionService;
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

import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channels/shopify")
@RequiredArgsConstructor
public class ShopifyOAuthController {

    private final ShopifyChannelConnectionService shopifyChannelConnectionService;
    private final ChannelConnectionLogService channelConnectionLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<ApiResponse<Map<String, String>>> authorize(@RequestParam String shop) {
        try {
            String authUrl = shopifyChannelConnectionService.buildAuthorizationUrl(shop);
            log.info("[ShopifyOAuth] returning authorization URL - shop={}", shop);
            return ResponseEntity.ok(ApiResponse.success(Map.of("url", authUrl)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("[ShopifyOAuth] authorize failed - shop={}, error={}", shop, e.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(HttpStatus.BAD_REQUEST.value(), e.getMessage()));
        }
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String shop,
            @RequestParam(required = false) String state) {

        log.info("[ShopifyOAuth] callback received - shop={}", shop);

        try {
            shopifyChannelConnectionService.connect(shop, code);
            log.info("[ShopifyOAuth] callback success - shop={}", shop);
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/channels?success=true"))
                    .build();
        } catch (AppException e) {
            log.error("[ShopifyOAuth] callback failed - shop={}, error={}", shop, e.getMessage());
            channelConnectionLogService.logFailure(
                    PlatformType.SHOPIFY, ChannelConnectionAction.CONNECT,
                    "Failed to connect Shopify channel", e.getMessage(), Map.of("shop", shop));
            String errorCode = e.getErrorCode() == ErrorCode.CHANNEL_IDENTITY_CONFLICT
                    ? "channel_identity_conflict" : "oauth_failed";
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/channels?error=" + errorCode))
                    .build();
        } catch (Exception e) {
            log.error("[ShopifyOAuth] callback failed - shop={}, error={}", shop, e.getMessage());
            channelConnectionLogService.logFailure(
                    PlatformType.SHOPIFY,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect Shopify channel",
                    e.getMessage(),
                    Map.of("shop", shop != null ? shop : "")
            );
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/channels?error=oauth_failed"))
                    .build();
        }
    }

}
