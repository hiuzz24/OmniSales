package fu.osms.sync.shopify.order;

import java.util.Map;

public interface ShopifyOrderMapper {
    ShopifyOrderWriteModel mapWebhook(String eventType, Map<String, Object> payload);
    ShopifyOrderWriteModel mapManual(Map<String, Object> payload);
}
