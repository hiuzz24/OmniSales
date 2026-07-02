package fu.osms.sync.lazada.dto;

import java.util.UUID;

public record LazadaSyncTask(UUID channelId, String reason) {

    public static LazadaSyncTask localChanges(UUID channelId) {
        return new LazadaSyncTask(channelId, "LOCAL_CHANGES");
    }
}
