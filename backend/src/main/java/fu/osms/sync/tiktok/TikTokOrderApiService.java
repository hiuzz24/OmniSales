package fu.osms.sync.tiktok;

import fu.osms.channel.entity.Channel;

import java.util.Map;
import java.util.Set;

public interface TikTokOrderApiService {

    Map<String, Object> getOrderDetail(Channel channel, String orderId);

    Eligibility getSellerCancelEligibility(Channel channel, String orderId);

    Map<String, Object> shipPackage(Channel channel, String packageId);

    Map<String, Object> cancelOrder(Channel channel, String orderId, String cancelReason);

    record Eligibility(boolean eligible, Set<String> reasonNames, String warningMessage) {
    }
}
