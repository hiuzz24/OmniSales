package fu.osms.sync.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncResult {

    private int totalChannels;
    private int successCount;
    private int failedCount;

    @Builder.Default
    private List<ChannelSyncDetail> details = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChannelSyncDetail {
        private String channelId;
        private String channelName;
        private String platform;
        private boolean success;
        private String errorMessage;
    }
}
