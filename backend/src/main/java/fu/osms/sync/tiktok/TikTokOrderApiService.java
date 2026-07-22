package fu.osms.sync.tiktok;

import fu.osms.channel.entity.Channel;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.time.OffsetDateTime;

public interface TikTokOrderApiService {

    Map<String, Object> getOrderDetail(Channel channel, String orderId);

    List<Map<String, Object>> getOrderDetails(Channel channel, List<String> orderIds);

    OrderSearchPage searchOrders(Channel channel, OffsetDateTime from, OffsetDateTime to, String pageToken);

    Eligibility getSellerCancelEligibility(Channel channel, String orderId);

    Map<String, Object> shipPackage(Channel channel, String packageId);

    Map<String, Object> cancelOrder(Channel channel, String orderId, String cancelReason);

    record Eligibility(boolean eligible, Set<String> reasonNames, String warningMessage) {
    }

    record OrderSearchPage(List<String> orderIds, String nextPageToken) {
    }
}
