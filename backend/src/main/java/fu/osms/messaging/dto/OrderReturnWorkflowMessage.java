package fu.osms.messaging.dto;

import java.util.UUID;

public record OrderReturnWorkflowMessage(UUID messageId, UUID returnId) {
}
