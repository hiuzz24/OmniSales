package fu.osms.sync.webhook.impl;

import fu.osms.common.enums.PlatformType;
import fu.osms.sync.entity.WebhookEvent;
import fu.osms.sync.service.PlatformOrderWebhookProcessor;
import fu.osms.sync.tiktok.TikTokOrderApiService;
import fu.osms.sync.webhook.WebhookPayloadUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TikTokOrderWebhookProcessor implements PlatformOrderWebhookProcessor {

    private final TikTokOrderApiService tikTokOrderApiService;
    private final TikTokOrderWebhookWriter tikTokOrderWebhookWriter;

    @Override
    public PlatformType getPlatform() {
        return PlatformType.TIKTOK;
    }

    @Override
    public String process(WebhookEvent event) {
        String orderId = WebhookPayloadUtils.text(WebhookPayloadUtils.firstPresentInData(event.getRawPayload(), "order_id"));
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("TikTok webhook payload is missing data.order_id");
        }

        Map<String, Object> detail = tikTokOrderApiService.getOrderDetail(event.getChannel(), orderId);
        tikTokOrderWebhookWriter.write(event.getId(), detail);
        return "PROCESSED";
    }
}
