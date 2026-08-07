package fu.osms.channel.controller;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.common.exception.AppException;
import fu.osms.common.exception.ErrorCode;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyOAuthService;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
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
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channels/shopify")
@RequiredArgsConstructor
public class ShopifyOAuthController {

    private final ShopifyOAuthService shopifyOAuthService;
    private final ShopifyApiClient shopifyApiClient;
    private final ShopifyShopDomainNormalizer shopDomainNormalizer;
    private final ChannelService channelService;
    private final ChannelConnectionLogService channelConnectionLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    @PreAuthorize("hasAnyRole('OWNER', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, String>>> authorize(@RequestParam String shop) {
        try {
            String normalizedShop = shopDomainNormalizer.normalizeHandle(shop);
            log.info("[ShopifyOAuth] authorize - shop={}", normalizedShop);
            String authUrl = shopifyOAuthService.buildAuthorizationUrl(normalizedShop);
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
            String normalizedShop = shopDomainNormalizer.normalizeHandle(shop);
            String accessToken = shopifyOAuthService.exchangeCodeForToken(normalizedShop, code);
            ensureReadLocationsScope(normalizedShop, accessToken);
            ChannelResponse channel = channelService.connectShopify(normalizedShop, accessToken);
            channelService.registerShopifyWebhooks(normalizedShop, accessToken, channel.getId());
            log.info("[ShopifyOAuth] callback success - shop={}", normalizedShop);
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

    private void ensureReadLocationsScope(String shop, String accessToken) {
        List<String> grantedScopes = shopifyApiClient.listAccessScopes(shop, accessToken);
        log.info("[ShopifyOAuth] callback granted scopes — shop={}, scopes={}", shop, grantedScopes);
        boolean hasReadLocations = grantedScopes.stream()
                .anyMatch(scope -> "read_locations".equalsIgnoreCase(scope));
        if (!hasReadLocations) {
            throw new IllegalStateException(
                    "Shopify chưa cấp quyền read_locations cho token mới. "
                            + "Hãy kiểm tra đúng app/API key, OAuth scopes có read_locations, "
                            + "sau đó uninstall app trong Shopify Admin và kết nối lại. "
                            + "Scopes Shopify cấp hiện tại: " + grantedScopes);
        }
    }

}
