package fu.osms.inventory.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of syncing warehouse info to all connected marketplace channels.
 */
@Data
@Builder
public class WarehouseMarketplaceSyncResult {

    /** Summary: true if ALL channels succeeded. */
    private boolean allSucceeded;

    /** Per-channel sync results. */
    private List<ChannelSyncStatus> channels;

    @Data
    @Builder
    public static class ChannelSyncStatus {
        private String channelId;
        private String channelName;
        private String platform;
        private boolean success;
        /** True when platform API does not support warehouse update (Lazada, TikTok). */
        private boolean savedLocallyOnly;
        /** Human-readable error message when success=false. */
        private String error;
        /** What was sent to the channel (for audit/display). */
        private String syncedName;
        private String syncedAddress;
        private String syncedContactName;
        private String syncedPhone;
    }
}
