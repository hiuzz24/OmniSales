package fu.osms.messaging.dto;

import java.util.UUID;

public record ProductSyncMessage(UUID productId, UUID channelId) {
}
