package fu.osms.sync.tiktok;

import fu.osms.order.dto.response.BuyerCancellationResponse;
import fu.osms.sync.entity.WebhookEvent;

import java.util.UUID;

public interface TikTokBuyerCancellationService {
    void handleWebhook(WebhookEvent event);

    BuyerCancellationResponse get(UUID orderId);

    BuyerCancellationResponse approve(UUID orderId);

    BuyerCancellationResponse reject(UUID orderId, String reasonCode, String comment);
}
