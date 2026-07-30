package fu.osms.sync.tiktok.returning;

import java.util.Map;
import java.util.UUID;

public interface TikTokReturnApiService {

    Map<String, Object> getReturn(UUID channelId, String externalReturnId);
}
