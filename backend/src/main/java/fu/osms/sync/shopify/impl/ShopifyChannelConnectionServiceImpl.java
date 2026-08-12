package fu.osms.sync.shopify.impl;

import fu.osms.channel.dto.response.ChannelResponse;
import fu.osms.channel.service.ChannelService;
import fu.osms.sync.shopify.ShopifyApiClient;
import fu.osms.sync.shopify.ShopifyChannelConnectionService;
import fu.osms.sync.shopify.ShopifyOAuthService;
import fu.osms.sync.shopify.ShopifyShopDomainNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShopifyChannelConnectionServiceImpl implements ShopifyChannelConnectionService {

    private final ShopifyOAuthService shopifyOAuthService;
    private final ShopifyApiClient shopifyApiClient;
    private final ShopifyShopDomainNormalizer shopDomainNormalizer;
    private final ChannelService channelService;

    @Override
    public String buildAuthorizationUrl(String shop) {
        String normalizedShop = shopDomainNormalizer.normalizeHandle(shop);
        return shopifyOAuthService.buildAuthorizationUrl(normalizedShop);
    }

    @Override
    public ChannelResponse connect(String shop, String authorizationCode) {
        String normalizedShop = shopDomainNormalizer.normalizeHandle(shop);
        String accessToken = shopifyOAuthService.exchangeCodeForToken(normalizedShop, authorizationCode);
        ensureReadLocationsScope(normalizedShop, accessToken);

        ChannelResponse channel = channelService.connectShopify(normalizedShop, accessToken);
        channelService.registerShopifyWebhooks(normalizedShop, accessToken, channel.getId());
        return channel;
    }

    private void ensureReadLocationsScope(String shop, String accessToken) {
        List<String> grantedScopes = shopifyApiClient.listAccessScopes(shop, accessToken);
        log.info("[ShopifyChannelConnection] granted scopes - shop={}, scopes={}", shop, grantedScopes);
        boolean hasReadLocations = grantedScopes.stream()
                .anyMatch(scope -> "read_locations".equalsIgnoreCase(scope));
        if (!hasReadLocations) {
            throw new IllegalStateException(
                    "Shopify has not granted read_locations for the new token. "
                            + "Verify the app, API key and OAuth scopes, then reconnect the channel. "
                            + "Currently granted scopes: " + grantedScopes);
        }
    }
}
