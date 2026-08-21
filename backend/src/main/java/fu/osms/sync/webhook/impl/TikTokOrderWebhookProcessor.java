package fu.osms.sync.webhook.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.tiktok.TikTokBuyerCancellationService;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TikTokOrderWebhookProcessor implements PlatformOrderWebhookProcessor {

    private final TikTokOrderApiService tikTokOrderApiService;
    private final TikTokOrderWebhookWriter tikTokOrderWebhookWriter;
    private final TikTokBuyerCancellationService buyerCancellationService;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public String process(WebhookEvent event) {
        if ("TIKTOK_ORDER_CANCELLATION_STATUS_UPDATE".equalsIgnoreCase(event.getEventType())) {
            buyerCancellationService.handleWebhook(event);
            return "PROCESSED";
        }
        String orderId = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(event.getRawPayload(), "order_id"));
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("TikTok webhook payload is missing data.order_id");
        }

        Map<String, Object> detail = new LinkedHashMap<>(
                tikTokOrderApiService.getOrderDetail(event.getChannel(), orderId));
        applyAuthoritativeWebhookState(detail, event.getRawPayload());
        tikTokOrderWebhookWriter.write(event.getId(), detail);
        return "PROCESSED";
    }

    /**
     * Webhook status is newer than an eventually-consistent Order Detail response.
     * Overlay it so a cancellation cannot be reverted to AWAITING_SHIPMENT.
     */
    private void applyAuthoritativeWebhookState(Map<String, Object> detail, Map<String, Object> payload) {
        Map<String, Object> data = WebhookPayloadUtils.copyMap(payload == null ? null : payload.get("data"));
        String webhookOrderStatus = WebhookPayloadUtils.text(data.get("order_status"));
        String cancelStatus = WebhookPayloadUtils.text(data.get("cancel_status"));
        if (webhookOrderStatus != null && !webhookOrderStatus.isBlank()) {
            detail.put("order_status", webhookOrderStatus);
        } else if ("CANCELLATION_REQUEST_COMPLETE".equalsIgnoreCase(cancelStatus)) {
            detail.put("order_status", "CANCEL");
        }
        Object updateTime = data.get("update_time");
        if (updateTime == null) updateTime = payload == null ? null : payload.get("timestamp");
        if (updateTime != null) detail.put("update_time", updateTime);
    }
}
