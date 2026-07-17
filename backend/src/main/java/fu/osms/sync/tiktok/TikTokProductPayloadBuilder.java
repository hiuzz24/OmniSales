package fu.osms.sync.tiktok;

import fu.osms.sync.tiktok.dto.TikTokProductPayloadContext;

import java.util.Map;

public interface TikTokProductPayloadBuilder {
    Map<String, Object> buildPayload(TikTokProductPayloadContext context);
}
