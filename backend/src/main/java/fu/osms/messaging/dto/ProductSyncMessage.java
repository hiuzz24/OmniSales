package fu.osms.messaging.dto;

import java.util.UUID;

public record ProductSyncMessage(
        UUID messageId,
        UUID requestLogId,
        UUID productId,
        UUID channelId
) {
}
