package fu.osms.sync.tiktok.order;

import fu.osms.channel.entity.Channel;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record TikTokOrderWriteContext(
        Channel channel,
        TikTokOrderWriteSource source,
        String webhookEventType,
        Map<String, Object> reverseData,
        Long webhookTimestamp
) {
    public TikTokOrderWriteContext {
        reverseData = reverseData == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(reverseData));
    }

    public static TikTokOrderWriteContext manual(Channel channel) {
        return new TikTokOrderWriteContext(channel, TikTokOrderWriteSource.MANUAL_PULL, null, Map.of(), null);
    }
}
