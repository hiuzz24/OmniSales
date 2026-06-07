package fu.osms.channel.dto.response;

import fu.osms.common.enums.SyncStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelProductVariantResponse {

    private UUID id;
    private UUID channelProductId;
    private UUID variantId;
    private String variantSku;
    private String externalVariantId;
    private String externalSku;
    private BigDecimal externalPrice;
    private SyncStatus syncStatus;
    private OffsetDateTime lastSyncedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
