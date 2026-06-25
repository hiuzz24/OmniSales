package fu.osms.channel.controller;

import fu.osms.channel.service.ChannelService;
import fu.osms.common.dto.ApiResponse;
import fu.osms.sync.shopify.ShopifyOAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/channels/shopify")
@RequiredArgsConstructor
public class ShopifyOAuthController {

    private final ShopifyOAuthService shopifyOAuthService;
    private final ChannelService channelService;

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
            channelService.connectShopify(shop, accessToken);
            log.info("[ShopifyOAuth] callback success — shop={}", shop);
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/channels?success=true"))
                    .build();
        } catch (Exception e) {
            log.error("[ShopifyOAuth] callback failed — shop={}, error={}", shop, e.getMessage());
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/channels?error=oauth_failed"))
                    .build();
        }
    }
}
