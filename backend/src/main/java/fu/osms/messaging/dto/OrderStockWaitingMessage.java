package fu.osms.messaging.dto;

import java.util.Set;
import java.util.UUID;

public record OrderStockWaitingMessage(UUID messageId, Set<UUID> variantIds) {
}
