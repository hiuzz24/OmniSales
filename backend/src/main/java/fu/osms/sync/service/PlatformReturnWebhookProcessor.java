package fu.osms.sync.service;

import fu.osms.sync.entity.WebhookEvent;

public interface PlatformReturnWebhookProcessor {
    boolean supports(WebhookEvent event);

    String process(WebhookEvent event);
}
