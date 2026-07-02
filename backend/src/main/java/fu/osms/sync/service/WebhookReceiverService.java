package fu.osms.sync.service;

import fu.osms.common.dto.PageResponse;
import fu.osms.common.enums.PlatformType;
import fu.osms.sync.dto.WebhookEventResponse;
import fu.osms.sync.dto.WebhookReceiveResult;

import java.util.Map;
import java.util.UUID;

public interface WebhookReceiverService {
    WebhookReceiveResult receive(PlatformType platform, Map<String, String> headers, String rawBody);

    PageResponse<WebhookEventResponse> search(PlatformType platform, String status, String eventType, UUID channelId, int page, int size);
}
