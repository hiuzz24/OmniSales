package fu.osms.sync.shopify.order;

import fu.osms.channel.entity.Channel;

import java.time.OffsetDateTime;

public interface ShopifyOrderApiClient {
    ShopifyOrderPage firstPage(Channel channel, OffsetDateTime from, OffsetDateTime to);
    ShopifyOrderPage nextPage(Channel channel, String pageInfo);
}
