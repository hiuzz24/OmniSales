package fu.osms.messaging.dto;

import java.util.Set;
import java.util.UUID;

public record InventoryPushMessage(Set<UUID> variantIds, UUID excludedChannelId) {
}
