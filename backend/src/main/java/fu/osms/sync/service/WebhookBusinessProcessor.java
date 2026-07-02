package fu.osms.sync.service;

import fu.osms.sync.entity.WebhookEvent;

public interface WebhookBusinessProcessor {
    String process(WebhookEvent event);
}
