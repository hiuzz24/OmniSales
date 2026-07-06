package fu.osms.channel.controller;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.channel.service.ChannelConnectionLogService;
import fu.osms.channel.enums.ChannelConnectionAction;
import fu.osms.common.dto.ApiResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channels/shopify")
@RequiredArgsConstructor
public class ShopifyOAuthController {

    private final ShopifyOAuthService shopifyOAuthService;
    private final ShopifyApiClient shopifyApiClient;
    private final ChannelService channelService;
    private final ChannelConnectionLogService channelConnectionLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @GetMapping("/authorize")
    public ResponseEntity<ApiResponse<Map<String, String>>> authorize(@RequestParam String shop) {
        log.info("[ShopifyOAuth] authorize — shop={}", shop);
        String authUrl = shopifyOAuthService.buildAuthorizationUrl(shop);
        return ResponseEntity.ok(ApiResponse.success(Map.of("url", authUrl)));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String shop,
            @RequestParam(required = false) String state) {

        log.info("[ShopifyOAuth] callback received — shop={}", shop);

        try {
            String accessToken = shopifyOAuthService.exchangeCodeForToken(shop, code);
            ensureReadLocationsScope(shop, accessToken);
            ChannelResponse channel = channelService.connectShopify(shop, accessToken);
            channelService.registerShopifyWebhooks(shop, accessToken, channel.getId());
            log.info("[ShopifyOAuth] callback success — shop={}", shop);
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/channels?success=true"))
                    .build();
        } catch (Exception e) {
            log.error("[ShopifyOAuth] callback failed — shop={}, error={}", shop, e.getMessage());
            channelConnectionLogService.logFailure(
                    PlatformType.SHOPIFY,
                    ChannelConnectionAction.CONNECT,
                    "Failed to connect Shopify channel",
                    e.getMessage(),
                    Map.of("shop", shop != null ? shop : "")
            );
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/channels?error="
                            + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8)))
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
