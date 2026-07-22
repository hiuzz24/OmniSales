package fu.osms.sync.shopify.order;

import java.util.List;
import java.util.Map;

public record ShopifyOrderPage(List<Map<String, Object>> orders, String nextPageInfo) {
}
