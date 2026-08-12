package fu.osms.sync.shopify;

import fu.osms.channel.dto.response.ChannelResponse;

public interface ShopifyChannelConnectionService {

    String buildAuthorizationUrl(String shop);

    ChannelResponse connect(String shop, String authorizationCode);
}
