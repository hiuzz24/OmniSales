package fu.osms.channel.dto.response;

import fu.osms.common.enums.PlatformType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelSyncDetailResponse {

    private UUID channelId;
    private String channelName;
    private PlatformType platform;
    private String sellerId;
    private String shopId;
    private String shopDomain;
    private String status;
    private String message;
    private int productCount;
    private int variantCount;
    private int warehouseCount;
    private int pushedVariantCount;
    private long durationMs;
}
