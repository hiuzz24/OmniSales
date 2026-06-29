package fu.osms.channel.dto.response;

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
public class ChannelImportSyncResponse {

    private UUID channelId;
    private UUID syncLogId;
    private int productCount;
    private int variantCount;
    private int warehouseCount;
    private int pushedVariantCount;
    private String status;
    private String message;
}
