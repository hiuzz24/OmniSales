package fu.osms.sync.webhook;

import fu.osms.channel.entity.Channel;
import fu.osms.common.enums.PlatformType;

import java.util.Map;
import java.util.Optional;

public interface PlatformWebhookHandler {
    PlatformType getPlatform();

    boolean verify(Map<String, String> headers, String rawBody);

    String extractEventType(Map<String, String> headers, Map<String, Object> payload);

    String extractExternalEventId(Map<String, String> headers, Map<String, Object> payload, String rawBody);

    Optional<Channel> resolveChannel(Map<String, String> headers, Map<String, Object> payload);
}
