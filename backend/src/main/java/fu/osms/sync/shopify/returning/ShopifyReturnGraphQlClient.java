package fu.osms.sync.shopify.returning;

import fu.osms.orderreturn.model.ReturnActionContext;

import java.util.Map;
import java.util.UUID;

public interface ShopifyReturnGraphQlClient {
    Map<String, Object> getReturn(UUID channelId, String returnGid);

    Map<String, Object> approve(UUID channelId, String returnGid);

    Map<String, Object> decline(UUID channelId, String returnGid, String reason);

    Map<String, Object> process(UUID channelId, String returnGid, ReturnActionContext context);
}
