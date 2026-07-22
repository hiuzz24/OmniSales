package fu.osms.sync.tiktok.order.impl;

import fu.osms.order.enums.OrderStatus;
import fu.osms.sync.tiktok.order.TikTokOrderMetadataMapper;
import fu.osms.sync.tiktok.order.TikTokOrderWriteContext;
import fu.osms.sync.tiktok.order.TikTokOrderWriteModel;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
public class TikTokOrderMetadataMapperImpl implements TikTokOrderMetadataMapper {
    @Override
    public Map<String, Object> merge(Map<String, Object> existing, TikTokOrderWriteContext context,
                                     TikTokOrderWriteModel model, boolean includeSnapshot) {
        Map<String, Object> root = new HashMap<>(existing == null ? Map.of() : existing);
        Map<String, Object> tikTok = new HashMap<>(WebhookPayloadUtils.copyMap(root.get("tiktok")));
        if (context.webhookEventType() != null && context.webhookEventType().contains("REVERSE")) {
            tikTok.put("reverse", context.reverseData());
            String cancelStatus = text(context.reverseData(), "cancel_status", "reverse_status", "status");
            if (cancelStatus != null) tikTok.put("cancelStatus", cancelStatus);
            if (context.webhookTimestamp() != null) {
                tikTok.put("lastReverseUpdateTime", context.webhookTimestamp());
                tikTok.put("lastCancellationUpdateTime", context.webhookTimestamp());
            }
        }
        if (includeSnapshot) {
            tikTok.put("rawOrderStatus", model.rawStatus());
            if (model.updateTime() != null) tikTok.put("lastOrderUpdateTime", model.updateTime());
            tikTok.putAll(model.detailMetadata());
            if (model.status() == OrderStatus.CANCELLED) {
                tikTok.put("pendingConfirmation", false);
                tikTok.put("cancelStatus", "CANCELLATION_REQUEST_COMPLETE");
                tikTok.put("cancelConfirmedAt", OffsetDateTime.now().toString());
            }
        }
        root.put("tiktok", tikTok);
        return root;
    }

    private String text(Map<String, Object> value, String... keys) {
        return WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresent(value, keys));
    }
}
