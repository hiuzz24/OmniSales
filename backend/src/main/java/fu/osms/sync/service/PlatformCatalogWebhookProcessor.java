package fu.osms.sync.service;

import fu.osms.common.enums.PlatformType;
import fu.osms.sync.entity.WebhookEvent;

public interface PlatformCatalogWebhookProcessor {
    PlatformType getPlatform();

    boolean supports(WebhookEvent event);

    String process(WebhookEvent event);
}
