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

    ShippingDocumentResult getPackageShippingDocument(Channel channel, String packageId);

    Map<String, Object> cancelOrder(Channel channel, String orderId, String cancelReason);

    Cancellation searchCancellation(Channel channel, String cancelId);

    CancellationDecisionEligibility getCancellationDecisionEligibility(Channel channel, String cancelId);

    Map<String, Object> approveCancellation(Channel channel, String cancelId, String idempotencyKey);

    Map<String, Object> rejectCancellation(Channel channel, String cancelId, String reasonCode,
                                           String comment, String idempotencyKey);

    record Eligibility(boolean eligible, Set<String> reasonNames, String warningMessage) {
    }

    record OrderSearchPage(List<String> orderIds, String nextPageToken) {
    }

    record ShippingDocumentResult(String code, String message, String documentUrl) {
    }

    record Cancellation(String cancelId, String orderId, String cancelStatus,
                        String initiatorRole, String lastActorRole, String sellerNextAction,
                        Long updateTime) {
    }

    record CancellationDecisionEligibility(ActionDecision approve, ActionDecision reject) {
    }

    record ActionDecision(boolean eligible, String warningMessage, List<DecisionReason> reasons) {
    }

    record DecisionReason(String code, String label) {
    }
}
