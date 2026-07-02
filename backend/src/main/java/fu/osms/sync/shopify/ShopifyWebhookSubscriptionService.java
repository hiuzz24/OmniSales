package fu.osms.sync.shopify;

import fu.osms.sync.dto.shopify.WebhookRegistrationResult;

import java.util.List;
import java.util.Map;

public interface ShopifyWebhookSubscriptionService {
    WebhookRegistrationResult registerWebhooks(String shopDomain, String accessToken);

    void unregisterWebhooks(String shopDomain, String accessToken, List<Map<String, Object>> savedWebhooks);
}
