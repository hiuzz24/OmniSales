package fu.osms.sync.service;

import fu.osms.common.enums.PlatformType;
import fu.osms.sync.entity.WebhookEvent;

public interface PlatformOrderWebhookProcessor {
    PlatformType getPlatform();

    String process(WebhookEvent event);
}
