package fu.osms.sync.tiktok.returning;

import fu.osms.orderreturn.model.ReturnRejectOptions;

import java.util.Map;
import java.util.UUID;

public interface TikTokReturnApiService {

    Map<String, Object> getReturn(UUID channelId, String externalReturnId);

    ReturnRejectOptions getRejectOptions(UUID channelId, String externalReturnId);
}
